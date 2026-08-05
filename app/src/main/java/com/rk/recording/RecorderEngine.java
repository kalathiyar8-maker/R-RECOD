package com.rk.recording;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.view.Surface;

import java.io.FileDescriptor;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Screen (H264 via input surface) + audio recorder muxed to MP4.
 * Audio: internal/app sound (AudioPlaybackCapture, STEREO), microphone (mono),
 * or both mixed. Output is stereo whenever internal audio is on, else mono.
 */
public class RecorderEngine {

    public interface ErrorListener { void onError(String msg); }

    private static final int SAMPLE_RATE = 44100;
    private static final int A_BITRATE   = 160000;
    private static final int FRAMES      = 1024;   // audio frames per read

    private final int width, height, bitrate, frameRate;
    private final boolean useMic, useInternal, cleanVoice;
    private final MediaProjection projection;
    private final FileDescriptor outFd;
    private final ErrorListener errorListener;

    private MediaCodec videoCodec, audioCodec;
    private Surface inputSurface;
    private MediaMuxer muxer;
    private final Object muxerLock = new Object();
    private int expectedTracks = 1, addedTracks = 0;
    private boolean muxerStarted = false;
    private int videoTrack = -1, audioTrack = -1;

    private AudioRecord micRec, playRec;
    private int outChannels = 1;
    private Thread videoThread, audioThread;
    private volatile boolean running = false;
    private long firstVideoPtsUs = -1;
    private long audioFrames = 0;

    public RecorderEngine(MediaProjection projection, FileDescriptor outFd,
                          int width, int height, int bitrate, int frameRate,
                          boolean useMic, boolean useInternal, boolean cleanVoice,
                          ErrorListener l) {
        this.projection = projection; this.outFd = outFd;
        this.width = width; this.height = height; this.bitrate = bitrate; this.frameRate = frameRate;
        this.useMic = useMic; this.useInternal = useInternal; this.cleanVoice = cleanVoice;
        this.errorListener = l;
    }

    public Surface prepare() throws Exception {
        MediaFormat vf = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height);
        vf.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        vf.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
        vf.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
        vf.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2);
        videoCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
        videoCodec.configure(vf, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        inputSurface = videoCodec.createInputSurface();
        videoCodec.start();

        setupAudioRecorders();
        boolean anyAudio = (playRec != null) || (micRec != null);
        if (anyAudio) {
            outChannels = (playRec != null) ? 2 : 1;   // stereo when internal audio present
            expectedTracks = 2;
            MediaFormat af = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE, outChannels);
            af.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            af.setInteger(MediaFormat.KEY_BIT_RATE, A_BITRATE);
            audioCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
            audioCodec.configure(af, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        }

        muxer = new MediaMuxer(outFd, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        return inputSurface;
    }

    private void setupAudioRecorders() {
        if (useInternal) {
            try {
                int minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT);
                AudioPlaybackCaptureConfiguration conf =
                        new AudioPlaybackCaptureConfiguration.Builder(projection)
                                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                                .build();
                AudioFormat fmt = new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_IN_STEREO).build();
                AudioRecord r = new AudioRecord.Builder()
                        .setAudioPlaybackCaptureConfig(conf)
                        .setAudioFormat(fmt)
                        .setBufferSizeInBytes(Math.max(minBuf, FRAMES * 2 * 2 * 4)).build();
                if (r.getState() == AudioRecord.STATE_INITIALIZED) playRec = r; else r.release();
            } catch (Exception e) { playRec = null; }
        }
        if (useMic) {
            try {
                int minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
                int src = cleanVoice ? MediaRecorder.AudioSource.VOICE_COMMUNICATION
                                     : MediaRecorder.AudioSource.MIC;
                AudioRecord r = new AudioRecord(src, SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                        Math.max(minBuf, FRAMES * 2 * 4));
                if (r.getState() == AudioRecord.STATE_INITIALIZED) micRec = r; else r.release();
            } catch (Exception e) { micRec = null; }
        }
    }

    public void start() {
        running = true;
        videoThread = new Thread(this::drainVideoLoop, "rk-video");
        videoThread.start();
        if (audioCodec != null) {
            audioCodec.start();
            if (playRec != null) playRec.startRecording();
            if (micRec != null)  micRec.startRecording();
            audioThread = new Thread(this::audioLoop, "rk-audio");
            audioThread.start();
        }
    }

    public void stop() {
        running = false;
        try { videoCodec.signalEndOfInputStream(); } catch (Exception ignored) {}
        try { if (videoThread != null) videoThread.join(2000); } catch (Exception ignored) {}
        try { if (audioThread != null) audioThread.join(2000); } catch (Exception ignored) {}
        release();
    }

    // ---------- video ----------
    private void drainVideoLoop() {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        try {
            while (true) {
                int out = videoCodec.dequeueOutputBuffer(info, 10000);
                if (out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    synchronized (muxerLock) { videoTrack = muxer.addTrack(videoCodec.getOutputFormat()); maybeStartMuxer(); }
                } else if (out >= 0) {
                    ByteBuffer buf = videoCodec.getOutputBuffer(out);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) info.size = 0;
                    if (info.size > 0 && buf != null) {
                        if (firstVideoPtsUs < 0) firstVideoPtsUs = info.presentationTimeUs;
                        info.presentationTimeUs -= firstVideoPtsUs;
                        writeSample(videoTrack, buf, info);
                    }
                    videoCodec.releaseOutputBuffer(out, false);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break;
                }
            }
        } catch (Exception e) { fail("video: " + e.getMessage()); }
    }

    // ---------- audio ----------
    private void audioLoop() {
        short[] in = new short[FRAMES * 2];   // stereo internal buffer
        short[] mc = new short[FRAMES];       // mono mic buffer
        short[] out = new short[FRAMES * 2];  // output (up to stereo)
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        try {
            while (running) {
                int lenI = (playRec != null) ? playRec.read(in, 0, FRAMES * 2) : 0;
                int lenM = (micRec  != null) ? micRec.read(mc, 0, FRAMES)      : 0;

                int frames, samples;
                if (playRec != null && micRec != null) {
                    int fI = Math.max(lenI, 0) / 2, fM = Math.max(lenM, 0);
                    frames = Math.min(fI, fM);
                    if (frames <= 0) continue;
                    for (int f = 0; f < frames; f++) {
                        out[2 * f]     = clamp(in[2 * f]     + mc[f]);
                        out[2 * f + 1] = clamp(in[2 * f + 1] + mc[f]);
                    }
                    samples = frames * 2;
                } else if (playRec != null) {
                    frames = Math.max(lenI, 0) / 2;
                    if (frames <= 0) continue;
                    System.arraycopy(in, 0, out, 0, frames * 2);
                    samples = frames * 2;
                } else {
                    frames = Math.max(lenM, 0);
                    if (frames <= 0) continue;
                    System.arraycopy(mc, 0, out, 0, frames);
                    samples = frames;
                }
                feedAudio(out, samples, frames);
                drainAudioEncoder(info, false);
            }
            feedAudioEos();
            drainAudioEncoder(info, true);
        } catch (Exception e) { fail("audio: " + e.getMessage()); }
    }

    private short clamp(int v) {
        if (v > 32767) return 32767;
        if (v < -32768) return -32768;
        return (short) v;
    }

    private void feedAudio(short[] buf, int samples, int frames) {
        int idx = audioCodec.dequeueInputBuffer(10000);
        if (idx >= 0) {
            ByteBuffer ib = audioCodec.getInputBuffer(idx);
            ib.clear();
            ib.order(ByteOrder.LITTLE_ENDIAN);
            ib.asShortBuffer().put(buf, 0, samples);
            long ptsUs = 1000000L * audioFrames / SAMPLE_RATE;
            audioCodec.queueInputBuffer(idx, 0, samples * 2, ptsUs, 0);
            audioFrames += frames;
        }
    }

    private void feedAudioEos() {
        long t0 = System.currentTimeMillis();
        int idx;
        do { idx = audioCodec.dequeueInputBuffer(10000); }
        while (idx < 0 && System.currentTimeMillis() - t0 < 500);
        if (idx >= 0) {
            long ptsUs = 1000000L * audioFrames / SAMPLE_RATE;
            audioCodec.queueInputBuffer(idx, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
        }
    }

    private void drainAudioEncoder(MediaCodec.BufferInfo info, boolean waitEos) {
        while (true) {
            int out = audioCodec.dequeueOutputBuffer(info, 10000);
            if (out == MediaCodec.INFO_TRY_AGAIN_LATER) { if (waitEos) continue; return; }
            if (out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                synchronized (muxerLock) { audioTrack = muxer.addTrack(audioCodec.getOutputFormat()); maybeStartMuxer(); }
            } else if (out >= 0) {
                ByteBuffer buf = audioCodec.getOutputBuffer(out);
                if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) info.size = 0;
                if (info.size > 0 && buf != null) writeSample(audioTrack, buf, info);
                audioCodec.releaseOutputBuffer(out, false);
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) return;
            }
        }
    }

    // ---------- muxer ----------
    private void maybeStartMuxer() {
        addedTracks++;
        if (addedTracks >= expectedTracks && !muxerStarted) { muxer.start(); muxerStarted = true; }
    }

    private void writeSample(int track, ByteBuffer buf, MediaCodec.BufferInfo info) {
        synchronized (muxerLock) {
            if (!muxerStarted || track < 0 || muxer == null) return;
            buf.position(info.offset);
            buf.limit(info.offset + info.size);
            muxer.writeSampleData(track, buf, info);
        }
    }

    private void release() {
        try { if (videoCodec != null) { videoCodec.stop(); videoCodec.release(); } } catch (Exception ignored) {}
        try { if (audioCodec != null) { audioCodec.stop(); audioCodec.release(); } } catch (Exception ignored) {}
        try { if (playRec != null) { playRec.stop(); playRec.release(); } } catch (Exception ignored) {}
        try { if (micRec  != null) { micRec.stop();  micRec.release();  } } catch (Exception ignored) {}
        try { if (inputSurface != null) inputSurface.release(); } catch (Exception ignored) {}
        synchronized (muxerLock) {
            try { if (muxer != null) { if (muxerStarted) muxer.stop(); muxer.release(); } } catch (Exception ignored) {}
            muxer = null; muxerStarted = false;
        }
    }

    private void fail(String m) { if (errorListener != null) errorListener.onError(m); }
}
