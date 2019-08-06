package mp4video.video.com.video;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.util.Log;


import java.io.IOException;
import java.nio.ByteBuffer;

public class AudioEncoder {

    private static final String TAG = AudioEncoder.class.getSimpleName(); // H.264 Advanced Video
    private static final String MIME_TYPE = "audio/mp4a-latm"; // H.264 Advanced Video
    private MediaCodec encoder;
    private static final int SAMPLE_RATE = 16000;     // 采样率
    private static final int BIT_RATE = 32000;       // 比特率
    private static final int BUFFER_SIZE = 8192;     // 最小缓存
    private int minBuffer;
    // 录音对象
    private AudioRecord audioRecord;

    private IAACListener encoderListener;
    private boolean isRecording;

    public void setEncoderListener(IAACListener listener) {
        this.encoderListener = listener;
    }

    public AudioEncoder() {
    }

    private void createAudio() {
        minBuffer = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);

        audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                2 * minBuffer);
        Log.d(TAG,"audio record ready");
    }

    public MediaCodec getEncoder() {
        return encoder;
    }

    private void initMediaCodec() throws IOException {

        createAudio();

        MediaCodecInfo mCodecInfo = selectCodec(MIME_TYPE);
        if (mCodecInfo == null) {
            Log.e(TAG, "编码器不支持" + MIME_TYPE + "类型");
            return;
        }

        encoder = MediaCodec.createByCodecName(mCodecInfo.getName());
        MediaFormat format = new MediaFormat();
        format.setString(MediaFormat.KEY_MIME, "audio/mp4a-latm");
        format.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
        format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
        format.setInteger(MediaFormat.KEY_SAMPLE_RATE, SAMPLE_RATE);
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, BUFFER_SIZE);
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        encoder.start();
        Log.d(TAG,"audio media codec ready");
    }

    public boolean encode(byte[] buf, int length) {
        if (encoder == null) {
            return false;
        }

        try {

            int inputBufferIndex = encoder.dequeueInputBuffer(-1);
//			 Log.e(TAG, "inputBufferIndex: " + inputBufferIndex);
            if (inputBufferIndex >= 0) {
                ByteBuffer inputBuffer = encoder.getInputBuffer(inputBufferIndex);
                inputBuffer.clear();
                inputBuffer.put(buf, 0, length);
                //有的设备的编码器编出来的数据没有打时戳，
                //编码就有问题 ，编码器认为只有一帧，一直走INFO_TRY_AGAIN_LATER
                //所以queueInputBuffer 第4个参数不能是0
                encoder.queueInputBuffer(inputBufferIndex, 0, length, getPts(), 0);
            }
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            while (true) {
                int outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo,
                        0);
//                Log.e(TAG, "outputBufferIndex: " + outputBufferIndex);
                if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
//                    Log.e(TAG, "vedio encoder output format changed: " + encoder.getOutputFormat());
                    break;
                }

                if (outputBufferIndex < 0) {
//                    Log.e(TAG, "vedio 没有可用的outbuffer"+outputBufferIndex);
                    break;
                }

                if (outputBufferIndex >= 0) {
                    ByteBuffer outputBuffer = encoder.getOutputBuffer(outputBufferIndex);

                    byte[] outData = new byte[outputBuffer.remaining()];
                    outputBuffer.get(outData, 0, outData.length);
                    if (encoderListener != null && bufferInfo.presentationTimeUs != 0) {
                        encoderListener.onAAC(outData, bufferInfo.flags,bufferInfo.presentationTimeUs,bufferInfo.size,bufferInfo.offset);
                    }
                    Log.e(TAG, "audio presentationTimeUs:" + bufferInfo.presentationTimeUs + ",buffer.length:" + bufferInfo.size + ",buffer:" + outData.hashCode());

                    encoder.releaseOutputBuffer(outputBufferIndex, false);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    private long getPts() {
        return System.nanoTime() / 1000L;
    }

    public void start() {
        if (isRecording) {
            return;
        }
        isRecording = true;
        try {
            initMediaCodec();
            if (audioRecord != null) {
                audioRecord.startRecording();
                Log.d(TAG,"start record audio...");
            } else {
                isRecording = false;
                return;
            }
        } catch (IOException e) {
            e.printStackTrace();
            isRecording = false;
            return;
        }

        new RecordTread().start();
    }

    public void stop() {
        if (isRecording) {
            isRecording = false;

            Log.d(TAG,"stop and release audio record");
            try {
                if (audioRecord != null) {
                    audioRecord.stop();
                    audioRecord.release();
                    audioRecord = null;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }finally {
                Log.d(TAG, "stop and release codec");
                if (encoder != null) {
                    encoder.stop();
                    encoder.release();
                    encoder = null;
                }
            }
        }
    }

    private MediaCodecInfo selectCodec(String mimeType) {
        int numCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);

            if (!codecInfo.isEncoder()) {
                continue;
            }
            String[] types = codecInfo.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                if (types[j].equalsIgnoreCase(mimeType)) {
                    return codecInfo;
                }
            }
        }
        return null;
    }


    private class RecordTread extends Thread {

        public RecordTread(){
            setName("record thread");
        }

        @Override
        public void run() {
            Log.d(TAG,"start audio thread...");
            while (isRecording) {
                try {
                    byte[] compressedVoice = new byte[2 * minBuffer];
                    int iRead = read(compressedVoice, 0, 2 * minBuffer);
                    if (iRead > 0) {
                        encode(compressedVoice, iRead);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            Log.d(TAG,"quit audio thread...");
        }
    }

    public int read(byte[] audioData, int offsetInBytes, int sizeInBytes) {
        if (audioRecord != null) {
            return audioRecord.read(audioData, offsetInBytes, sizeInBytes);
        }
        return -1;
    }

    public interface IAACListener {
        void onAAC(byte[] bytes, int flag,long pts,int size,int offset);
    }
}
