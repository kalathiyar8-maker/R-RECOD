package com.rk.recording;

import android.app.RecoverableSecurityException;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Intent;
import android.content.IntentSender;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GalleryActivity extends AppCompatActivity {

    static class Item {
        long id; Uri uri; String name; long durationMs; long size; long dateSec;
    }

    private RecyclerView list;
    private TextView empty;
    private final List<Item> items = new ArrayList<>();
    private Adapter adapter;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ExecutorService thumbs = Executors.newFixedThreadPool(3);
    private final Handler main = new Handler(Looper.getMainLooper());
    private ActivityResultLauncher<IntentSenderRequest> deleteLauncher;
    private Uri pendingDelete;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_gallery);
        list = findViewById(R.id.list);
        empty = findViewById(R.id.empty);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new Adapter();
        list.setAdapter(adapter);

        deleteLauncher = registerForActivityResult(
                new ActivityResultContracts.StartIntentSenderForResult(),
                r -> {
                    if (r.getResultCode() == RESULT_OK && pendingDelete != null) {
                        try { getContentResolver().delete(pendingDelete, null, null); } catch (Exception ignored) {}
                    }
                    pendingDelete = null;
                    load();
                });
    }

    @Override protected void onResume() { super.onResume(); load(); }

    private void load() {
        io.execute(() -> {
            final List<Item> found = query();
            main.post(() -> {
                items.clear();
                items.addAll(found);
                adapter.notifyDataSetChanged();
                empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }

    private List<Item> query() {
        List<Item> out = new ArrayList<>();
        String[] proj = {
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.DATE_ADDED
        };
        String sel = MediaStore.Video.Media.RELATIVE_PATH + " LIKE ?";
        String[] args = {"%RK RECORDING%"};
        try (Cursor c = getContentResolver().query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI, proj, sel, args,
                MediaStore.Video.Media.DATE_ADDED + " DESC")) {
            if (c != null) {
                int iId = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID);
                int iN = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME);
                int iD = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION);
                int iS = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE);
                int iT = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED);
                while (c.moveToNext()) {
                    Item it = new Item();
                    it.id = c.getLong(iId);
                    it.name = c.getString(iN);
                    it.durationMs = c.getLong(iD);
                    it.size = c.getLong(iS);
                    it.dateSec = c.getLong(iT);
                    it.uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, it.id);
                    out.add(it);
                }
            }
        } catch (Exception ignored) {}
        return out;
    }

    private void play(Item it) {
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(it.uri, "video/mp4");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { startActivity(i); } catch (Exception e) { toast("No video player found"); }
    }

    private void share(Item it) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("video/mp4");
        i.putExtra(Intent.EXTRA_STREAM, it.uri);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { startActivity(Intent.createChooser(i, "Share recording")); }
        catch (Exception e) { toast("Cannot share"); }
    }

    private void delete(Item it) {
        try {
            int n = getContentResolver().delete(it.uri, null, null);
            if (n > 0) { toast("Deleted"); load(); }
        } catch (SecurityException se) {
            if (Build.VERSION.SDK_INT >= 29 && se instanceof RecoverableSecurityException) {
                pendingDelete = it.uri;
                IntentSender is = ((RecoverableSecurityException) se)
                        .getUserAction().getActionIntent().getIntentSender();
                deleteLauncher.launch(new IntentSenderRequest.Builder(is).build());
            } else {
                toast("Couldn't delete");
            }
        } catch (Exception e) { toast("Couldn't delete"); }
    }

    private void loadThumb(ImageView iv, Uri uri) {
        iv.setImageBitmap(null);
        iv.setTag(uri);
        thumbs.execute(() -> {
            Bitmap bmp = null;
            try {
                ContentResolver cr = getContentResolver();
                bmp = cr.loadThumbnail(uri, new Size(320, 180), null);
            } catch (Exception ignored) {}
            final Bitmap fb = bmp;
            main.post(() -> { if (uri.equals(iv.getTag()) && fb != null) iv.setImageBitmap(fb); });
        });
    }

    private String fmtMeta(Item it) {
        long s = it.durationMs / 1000;
        String dur = String.format(Locale.US, "%02d:%02d", s / 60, s % 60);
        String size = it.size >= 1048576
                ? String.format(Locale.US, "%.1f MB", it.size / 1048576f)
                : String.format(Locale.US, "%d KB", it.size / 1024);
        String date = android.text.format.DateFormat.getDateFormat(this)
                .format(new java.util.Date(it.dateSec * 1000L));
        return dur + " · " + size + " · " + date;
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    // ---------- adapter ----------
    class Adapter extends RecyclerView.Adapter<Adapter.VH> {
        class VH extends RecyclerView.ViewHolder {
            ImageView thumb; TextView name, meta; Button share, delete;
            VH(View v) {
                super(v);
                thumb = v.findViewById(R.id.thumb);
                name = v.findViewById(R.id.name);
                meta = v.findViewById(R.id.meta);
                share = v.findViewById(R.id.shareBtn);
                delete = v.findViewById(R.id.deleteBtn);
            }
        }
        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
            View v = LayoutInflater.from(p.getContext()).inflate(R.layout.item_recording, p, false);
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = (int) (10 * p.getResources().getDisplayMetrics().density);
            v.setLayoutParams(lp);
            return new VH(v);
        }
        @Override public void onBindViewHolder(@NonNull VH h, int pos) {
            Item it = items.get(pos);
            h.name.setText(it.name);
            h.meta.setText(fmtMeta(it));
            loadThumb(h.thumb, it.uri);
            h.itemView.setOnClickListener(v -> play(it));
            h.thumb.setOnClickListener(v -> play(it));
            h.share.setOnClickListener(v -> share(it));
            h.delete.setOnClickListener(v -> delete(it));
        }
        @Override public int getItemCount() { return items.size(); }
    }
}
