package com.jd.wly.intercom.app;

import android.app.Application;
import android.content.Context;
import android.provider.ContactsContract;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.BounceInterpolator;
import android.widget.ImageView;
import android.widget.Toast;

import com.jd.wly.intercom.R;
import com.jd.wly.intercom.util.Constants;
import com.jd.wly.intercom.util.PreferencesUtils;
import com.yhao.floatwindow.FloatWindow;
import com.yhao.floatwindow.MoveType;
import com.yhao.floatwindow.PermissionListener;
import com.yhao.floatwindow.Screen;
import com.yhao.floatwindow.ViewStateListener;

import static android.content.ContentValues.TAG;

public class App extends Application {

    private static Context context;

    public static Context getInstance() {
        return context;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        context = this;
    }
}
