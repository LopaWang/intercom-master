package com.jd.wly.intercom.input;

import android.media.AudioRecord;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import com.jd.wly.intercom.data.AudioData;
import com.jd.wly.intercom.data.MessageQueue;
import com.jd.wly.intercom.job.JobHandler;
import com.jd.wly.intercom.util.AECUtil;
import com.jd.wly.intercom.util.Constants;

import static android.content.ContentValues.TAG;

/**
 * 音频录制数据格式ENCODING_PCM_16BIT，返回数据类型为short[]
 *
 * @author yanghao1
 */
public class Recorder extends JobHandler {

    private AudioRecord audioRecord;
    // 音频大小
    private int inAudioBufferSize;
    // 录音标志
    private boolean isRecording = false;

    public Recorder(Handler handler) {
        super(handler);
        // 获取音频数据缓冲段大小
        if(audioRecord != null) {
            Log.i(TAG, "Recorder: =====" + audioRecord.getState());
            audioRecord.release();
            audioRecord = null;
        }else{
            Log.i(TAG, "Recorder:audioRecord ===== null");
        }
            inAudioBufferSize = AudioRecord.getMinBufferSize(
                    Constants.sampleRateInHz, Constants.inputChannelConfig, Constants.audioFormat);
            // 初始化音频录制
         if(audioRecord == null) {
             audioRecord = new AudioRecord(Constants.audioSource,
                     Constants.sampleRateInHz, Constants.inputChannelConfig, Constants.audioFormat, inAudioBufferSize);
         }
    }

    public boolean isRecording() {
        return isRecording;
    }

    public void setRecording(boolean recording) {
        isRecording = recording;
    }

    @Override
    public void run() {
        while (isRecording) {
            Log.i(TAG, "run: audioRecord.getRecordingState() = " + audioRecord.getRecordingState());
            if (audioRecord.getRecordingState() == AudioRecord.STATE_INITIALIZED) {
                 audioRecord.startRecording();
            }
            // 实例化音频数据缓冲
            short[] rawData = new short[inAudioBufferSize];
            audioRecord.read(rawData, 0, inAudioBufferSize);
            AudioData audioData = new AudioData(rawData);
            MessageQueue.getInstance(MessageQueue.ENCODER_DATA_QUEUE).put(audioData);
        }
    }

    @Override
    public void free() {
        // 释放音频录制资源
        audioRecord.stop();
        audioRecord.release();
        audioRecord = null;
    }
}
