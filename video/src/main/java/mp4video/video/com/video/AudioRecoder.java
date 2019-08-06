package mp4video.video.com.video;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

public class AudioRecoder{

    private String TAG = AudioRecoder.class.getSimpleName();
    private AudioEncoder audioEncoder;
    private static final int SAMPLE_RATE = 16000;     // 采样率
    private int minBuffer;
    // 录音对象
    private AudioRecord audioRecord;
    private boolean isRecording;

    public AudioRecoder(){
       // createAudio();
        audioEncoder = new AudioEncoder();
        recordThread.start();
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
                2*minBuffer);
    }


    public int read(byte[] audioData, int offsetInBytes, int sizeInBytes) {
        if(audioRecord != null) {
            return audioRecord.read(audioData, offsetInBytes, sizeInBytes);
        }
        return -1;
    }

    Thread recordThread = new Thread(){
        @Override
        public void run() {
            while(true){

                if (!isRecording){
                    synchronized (AudioRecoder.class){
                        try {
                            AudioRecoder.class.wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }

                try {
                    byte[] compressedVoice = new byte[2*minBuffer];
                    int iRead = read(compressedVoice, 0, 2*minBuffer);
                    if(iRead > 0 ){
                        if(audioEncoder != null){
                            audioEncoder.encode(compressedVoice, iRead);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    };
    public void start(){
        if (!isRecording){
            audioEncoder.start();
            createAudio();
            isRecording = true;

            try {
                if(audioRecord != null) {
                    audioRecord.startRecording();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            synchronized (AudioRecoder.class){
                AudioRecoder.class.notifyAll();
            }
        }
    }
    public void stop() {
        if (isRecording){
            isRecording = false;

            try {
                if(audioRecord != null) {
                    audioRecord.stop();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            if (audioRecord != null) {
                audioRecord.release();
                audioRecord = null;
            }
//            if(audioEncoder != null){
//                audioEncoder.stopAndRelease();
//            }
        }
    }
}
