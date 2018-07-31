package com.jd.wly.intercom;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.SoundPool;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Process;
import android.os.RemoteException;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.BounceInterpolator;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.jd.wly.intercom.floatwindow.FloatWindow;
import com.jd.wly.intercom.floatwindow.MoveType;
import com.jd.wly.intercom.floatwindow.PermissionListener;
import com.jd.wly.intercom.floatwindow.Screen;
import com.jd.wly.intercom.floatwindow.ViewStateListener;
import com.jd.wly.intercom.service.IIntercomCallback;
import com.jd.wly.intercom.service.IIntercomService;
import com.jd.wly.intercom.service.IntercomService;
import com.jd.wly.intercom.users.IntercomAdapter;
import com.jd.wly.intercom.users.IntercomUserBean;
import com.jd.wly.intercom.users.OverflowAdapter;
import com.jd.wly.intercom.users.VerticalSpaceItemDecoration;
import com.jd.wly.intercom.util.Constants;
import com.jd.wly.intercom.util.IPUtil;
import com.jd.wly.intercom.util.PreferencesUtils;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import widget.CNiaoToolBar;
import widget.OverflowHelper;

import static android.content.ContentValues.TAG;
import static android.content.Intent.ACTION_EDIT;

public class AudioActivity extends Activity implements View.OnTouchListener, View.OnClickListener {

    private RecyclerView localNetworkUser;
    private TextView currentIp;
    private ImageView chatRecord , imageView ;
    private TextView startIntercom;
    private CNiaoToolBar mToolbar;
    private OverflowHelper mOverflowHelper;
    private OverflowAdapter.OverflowItem[] mItems;


    private int mWeiChatAudioError ,mWeiChatAudioBegin , mWeiChatAudioUp;
    private SoundPool mSoundPool;//摇一摇音效

    private boolean isOtherPlaying = false;
    private boolean isFirstDown = true;


    private List<IntercomUserBean> userBeanList = new ArrayList<>();
    private IntercomAdapter intercomAdapter;
    private static final int MY_PERMISSIONS_REQUEST_RECORD_AUDIO = 1;
    private static final int MY_PERMISSIONS_REQUEST_FLOAT_WINDOW = 756232212;

    /**
     * onServiceConnected和onServiceDisconnected运行在UI线程中
     */
    private IIntercomService intercomService;
    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            intercomService = IIntercomService.Stub.asInterface(service);
            try {
                intercomService.registerCallback(intercomCallback);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            intercomService = null;
        }
    };

    /**
     * 被调用的方法运行在Binder线程池中，不能更新UI
     */
    private IIntercomCallback intercomCallback = new IIntercomCallback.Stub() {
        @Override
        public void findNewUser(String ipAddress) throws RemoteException {
            sendMsg2MainThread(ipAddress, FOUND_NEW_USER);
        }

        @Override
        public void removeUser(String ipAddress) throws RemoteException {
            sendMsg2MainThread(ipAddress, REMOVE_USER);
        }

        @Override
        public void isSpeak(String ipAddress) throws RemoteException {
            sendMsg2MainThread(ipAddress, IS_SPEAK);
        }

        @Override
        public void isNotSpeak(String ipAddress) throws RemoteException {
            sendMsg2MainThread(ipAddress, IS_NOT_SPEAK);
        }
    };

    private static final int FOUND_NEW_USER = 0;
    private static final int REMOVE_USER = 1;
    private static final int IS_SPEAK = 2;
    private static final int IS_NOT_SPEAK = 3;

    @Override
    public boolean onTouch(View v, MotionEvent event) {

        if (v == chatRecord || v == imageView) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                if (!isOtherPlaying) {
                    keyDown();
                }else {
                    mSoundPool.play(mWeiChatAudioError, 1, 1, 0, 0, 1);
                }
            }else if (event.getAction() == MotionEvent.ACTION_UP) {
                if (!isOtherPlaying) {
                    keyUp();
                }
        }
            return true;
        }
        return false;

    }



    /**
     * 跨进程回调更新界面
     */
    private static class DisplayHandler extends Handler {
        // 弱引用
        private WeakReference<AudioActivity> activityWeakReference;
        DisplayHandler(AudioActivity audioActivity) {
            activityWeakReference = new WeakReference<>(audioActivity);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            AudioActivity activity = activityWeakReference.get();
            if (activity != null) {
                if (msg.what == FOUND_NEW_USER) {
                    activity.foundNewUser((String) msg.obj);
                } else if (msg.what == REMOVE_USER) {
                    activity.removeExistUser((String) msg.obj);
                }else if (msg.what == IS_SPEAK) {
                    activity.releaseBTT((String) msg.obj);
                }else if (msg.what == IS_NOT_SPEAK) {
                    activity.releasePTT((String) msg.obj);
                }
            }
        }
    }

    private Handler handler = new DisplayHandler(this);

    /**
     * 发送Handler消息
     *
     * @param content 内容
     * @param msgWhat 消息类型
     */
    private void sendMsg2MainThread(String content, int msgWhat) {
        Message msg = new Message();
        msg.what = msgWhat;
        msg.obj = content;
        handler.sendMessage(msg);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audio);
        initFloat();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO},
                    MY_PERMISSIONS_REQUEST_RECORD_AUDIO);
        }else {
            initData();
            initView();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == MY_PERMISSIONS_REQUEST_RECORD_AUDIO) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(AudioActivity.this, getResources().getText(R.string.permission_success), Toast.LENGTH_SHORT).show();
                initData();
                initView();
            } else {
                Toast.makeText(AudioActivity.this,  getResources().getText(R.string.permission_fail), Toast.LENGTH_SHORT).show();
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }




    /**
     * 改变标题栏
     */
    private void initToolBar() {
        mToolbar = (CNiaoToolBar) findViewById(R.id.toolbar);
        mToolbar.hideSearchView();
        mToolbar.showTitleView();
        mToolbar.setTitle(R.string.WXTalk);
        mToolbar.getRightButton().setVisibility(View.VISIBLE);
        mToolbar.setRightButtonIcon(getResources().getDrawable(R.drawable.ic_list_normal));
        mToolbar.getRightButton().setOnClickListener(this);
        mToolbar.getRightButton().setTag(ACTION_EDIT);
        mOverflowHelper = new OverflowHelper(this);
    }


    private void initView() {
        initToolBar();
        // 设置用户列表
        localNetworkUser = (RecyclerView) findViewById(R.id.activity_audio_local_network_user_rv);
        localNetworkUser.setLayoutManager(new LinearLayoutManager(this));
        localNetworkUser.addItemDecoration(new VerticalSpaceItemDecoration(10));
        localNetworkUser.setItemAnimator(new DefaultItemAnimator());
        intercomAdapter = new IntercomAdapter(userBeanList);
        localNetworkUser.setAdapter(intercomAdapter);
        // 添加自己
        addNewUser(new IntercomUserBean(IPUtil.getLocalIPAddress(), "我"));

        startIntercom = (TextView) findViewById(R.id.start_intercom);
        startIntercom.setOnTouchListener(this);
        chatRecord = (ImageView) findViewById(R.id.chat_record);
        chatRecord.setOnTouchListener(this);
        // 设置当前IP地址
        currentIp = (TextView) findViewById(R.id.activity_audio_current_ip);
        currentIp.setText(IPUtil.getLocalIPAddress());

        //初始化SoundPool
        mSoundPool = new SoundPool(1, AudioManager.STREAM_SYSTEM, 5);
        mWeiChatAudioError = mSoundPool.load(this, R.raw.talkroom_sasasa, 1);
        mWeiChatAudioBegin = mSoundPool.load(this, R.raw.talkroom_begin_ham, 1);
        mWeiChatAudioUp = mSoundPool.load(this, R.raw.talkroom_up_ham, 1);

    }

    private void initData() {
        // 初始化AudioManager配置
        initAudioManager();
        // 启动Service
        Intent intent = new Intent(AudioActivity.this, IntercomService.class);
        startService(intent);
    }

    /**
     * 初始化AudioManager配置
     */
    private void initAudioManager() {
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
        audioManager.setSpeakerphoneOn(true);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
        }else {
            Intent intent = new Intent(AudioActivity.this, IntercomService.class);
            bindService(intent, serviceConnection, BIND_AUTO_CREATE);
        }

    }

    /**
     * 更新自身IP
     */
    public void updateMyself() {
        currentIp.setText(IPUtil.getLocalIPAddress());
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_F2 ||
                keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            if (!isOtherPlaying && isFirstDown) {
                keyDown();
                isFirstDown = false;
            }else if(isOtherPlaying){
                mSoundPool.play(mWeiChatAudioError, 1, 1, 0, 0, 1);
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_F2 ||
                keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            if (!isOtherPlaying) {
                keyUp();
                isFirstDown = true;
            }
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }


    private void keyDown() {
        try {
            if(intercomService  != null)
            intercomService.startRecord();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        chatRecord.setImageDrawable(getResources().getDrawable(R.drawable.se_icon_voice_pressed));
        startIntercom.setText(getResources().getText(R.string.leave_end));
        startIntercom.setTextColor(getResources().getColor(R.color.colorBlue));
        mSoundPool.play(mWeiChatAudioBegin, 1, 1, 0, 0, 1);
    }

    private void keyUp() {
        try {
            if(intercomService  != null)
            intercomService.stopRecord();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        chatRecord.setImageDrawable(getResources().getDrawable(R.drawable.se_icon_voice_default));
        startIntercom.setText(getResources().getText(R.string.press_speak));
        startIntercom.setTextColor(getResources().getColor(R.color.white));
        mSoundPool.play(mWeiChatAudioUp, 1, 1, 0, 0, 1);
    }

    @Override
    public void onBackPressed() {
        // 发送离开群组消息
        try {
            if(intercomService  != null)
            intercomService.leaveGroup();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        super.onBackPressed();
    }

    /**
     * 发现新的用户地址
     *
     * @param ipAddress
     */
    public void foundNewUser(String ipAddress) {
        IntercomUserBean userBean = new IntercomUserBean(ipAddress);
        if (!userBeanList.contains(userBean)) {
            addNewUser(userBean);
        }
    }

    /**
     * 删除用户
     *
     * @param ipAddress
     */
    public void removeExistUser(final String ipAddress) {
        IntercomUserBean userBean = new IntercomUserBean(ipAddress);
        if (userBeanList.contains(userBean)) {
            int position = userBeanList.indexOf(userBean);
            userBeanList.remove(position);
            intercomAdapter.notifyItemRemoved(position);
            intercomAdapter.notifyItemRangeChanged(0, userBeanList.size());
        }
    }

    /**
     * 增加新的用户
     *
     * @param userBean 新用户
     */
    public void addNewUser(IntercomUserBean userBean) {
        userBeanList.add(userBean);
        intercomAdapter.notifyItemInserted(userBeanList.size() - 1);
    }

    /**
     *抬起发送线程
     * @param ipAddress
     */
    public void releasePTT(final String ipAddress) {
        IntercomUserBean userBean = new IntercomUserBean(ipAddress);
        startIntercom.setText(getResources().getText(R.string.press_speak));
        chatRecord.setImageDrawable(getResources().getDrawable(R.drawable.comment_voice_selector));
        if (userBeanList.contains(userBean)) {
            int position = userBeanList.indexOf(userBean);
            View view= localNetworkUser.getChildAt(position);
            TextView tv = (TextView) view.findViewById(R.id.tv_speaking);
            tv.setVisibility(View.GONE);
            isOtherPlaying = false;
        }
    }
    /**
     *按下发送线程
     * @param ipAddress
     */
    public void releaseBTT(final String ipAddress) {
        IntercomUserBean userBean = new IntercomUserBean(ipAddress);
        startIntercom.setText(getResources().getText(R.string.other_speak));
        chatRecord.setImageDrawable(getResources().getDrawable(R.drawable.se_icon_voice_error));
        if (userBeanList.contains(userBean)) {
            int position = userBeanList.indexOf(userBean);
            View view= localNetworkUser.getChildAt(position);
            TextView tv = (TextView) view.findViewById(R.id.tv_speaking);
            tv.setVisibility(View.VISIBLE);
            isOtherPlaying = true;
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (intercomService != null && intercomService.asBinder().isBinderAlive()) {
            try {
                intercomService.unRegisterCallback(intercomCallback);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
            unbindService(serviceConnection);
        }
    }


    @Override
    public void onClick(View v) {
        if(v.getId() == R.id.toolbar_rightButton){
            controlPlusSubMenu();
        }
    }
    private void controlPlusSubMenu() {
        if (mOverflowHelper == null) {
            return;
        }

        if (mOverflowHelper.isOverflowShowing()) {
            mOverflowHelper.dismiss();
            return;
        }

        if(mItems == null) {
            initOverflowItems();
        }

        mOverflowHelper.setOverflowItems(mItems);
        mOverflowHelper .setOnOverflowItemClickListener(mOverflowItemCliclListener);
        mOverflowHelper.showAsDropDown(findViewById(R.id.toolbar_rightButton));
    }


    void initOverflowItems() {
        if (mItems == null) {
                mItems = new OverflowAdapter.OverflowItem[2];
                if(!PreferencesUtils.getBoolean(getApplicationContext(), Constants.ISOPENFLOAT)){
                    mItems[0] = new OverflowAdapter.OverflowItem( getString(R.string.open_float_talk));
                }else {
                    mItems[0] = new OverflowAdapter.OverflowItem( getString(R.string.close_float_talk));
                }
                mItems[1] = new OverflowAdapter.OverflowItem( getString(R.string.exit));
        }

    }

    private final AdapterView.OnItemClickListener mOverflowItemCliclListener = new AdapterView.OnItemClickListener() {

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position,
                                long id) {
            controlPlusSubMenu();

            OverflowAdapter.OverflowItem overflowItem= mItems[position];
            String title=overflowItem.getTitle();

            if (getString(R.string.open_float_talk).equals(title)) {
                mItems[0].setTitle(getString(R.string.close_float_talk));
                PreferencesUtils.putBoolean( getApplicationContext(), Constants.ISOPENFLOAT , true);
                openFloat();
            }else if (getString(R.string.close_float_talk).equals(title)) {
                mItems[0].setTitle(getString(R.string.open_float_talk));
                PreferencesUtils.putBoolean( getApplicationContext(), Constants.ISOPENFLOAT , false);
                closeFloat();
            } else if (getString(R.string.exit).equals(title)) {
                Toast.makeText(AudioActivity.this,"退出 ",Toast.LENGTH_SHORT).show();
            }
        }

    };

    private void openFloat(){
        //显示
        FloatWindow.get().show();
    }

    private void closeFloat(){
        //隐藏
        FloatWindow.get().hide();
    }
    private void initFloat(){
            imageView = new ImageView(getApplicationContext());
            imageView.setImageResource(R.drawable.se_icon_voice_default);
            FloatWindow
                    .with(getApplicationContext())
                    .setView(imageView)
                    .setWidth(Screen.width, 0.2f) //设置悬浮控件宽高
                    .setHeight(Screen.width, 0.2f)
                    .setX(Screen.width, 0.8f)
                    .setY(Screen.height, 0.3f)
                    .setMoveType(MoveType.active)
                    .setMoveStyle(500, new BounceInterpolator())
                    .setViewStateListener(mViewStateListener)
                    .setPermissionListener(mPermissionListener)
                    .setDesktopShow(true)
                    .build();

            imageView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Toast.makeText(getApplicationContext(), "onClick", Toast.LENGTH_SHORT).show();
                }
            });
            imageView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    Toast.makeText(getApplicationContext(), "长按", Toast.LENGTH_SHORT).show();
                    if (!isOtherPlaying) {
                        keyDown();
                    }else {
                        mSoundPool.play(mWeiChatAudioError, 1, 1, 0, 0, 1);
                    }
                    return false;
                }
            });
        }

    private PermissionListener mPermissionListener = new PermissionListener() {
        @Override
        public void onSuccess() {
            Log.d(TAG, "onSuccess");
        }

        @Override
        public void onFail() {
            Log.d(TAG, "onFail");
        }
    };


    private ViewStateListener mViewStateListener = new ViewStateListener() {
        @Override
        public void onPositionUpdate(int x, int y) {
            Log.d(TAG, "onPositionUpdate: x=" + x + " y=" + y);
        }

        @Override
        public void onShow() {
            Log.d(TAG, "onShow");
            if(!PreferencesUtils.getBoolean(getApplicationContext(),Constants.ISOPENFLOAT)){
                FloatWindow.get().hide();
            }else {
                FloatWindow.get().show();
            }
        }

        @Override
        public void onHide() {
            Log.d(TAG, "onHide");
        }

        @Override
        public void onDismiss() {
            Log.d(TAG, "onDismiss");
        }

        @Override
        public void onMoveAnimStart() {
            Log.d(TAG, "onMoveAnimStart");
        }

        @Override
        public void onMoveAnimEnd() {
            Log.d(TAG, "onMoveAnimEnd");
        }

        @Override
        public void onBackToDesktop() {
            Log.d(TAG, "onBackToDesktop");
        }

        @Override
        public void onUp() {
            if (!isOtherPlaying) {
                keyUp();
            }
        }
    };
}
