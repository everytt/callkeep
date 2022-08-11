package io.wazo.callkeep.activity.listener;

import android.os.SystemClock;
import android.view.View;

import java.util.Map;
import java.util.WeakHashMap;

public abstract class DebouncedOnClickListener implements View.OnClickListener {
    //private static final long MINIMUM_INTERVAL = 350;
    //KJH 2018-08-17
    //문자전송 및 연속 클릭 방지...
    private static final long MINIMUM_INTERVAL = 1200;
    private final Map<View, Long> mLastClickMap;

    public DebouncedOnClickListener() {
        mLastClickMap = new WeakHashMap<>();
    }

    public abstract void onDebouncedClick(View v);

    @Override
    public void onClick(View clickedView) {
        Long previousClickTimestamp = mLastClickMap.get(clickedView);
        long currentTimestamp = SystemClock.uptimeMillis();

        mLastClickMap.put(clickedView, currentTimestamp);
        if (previousClickTimestamp == null || (currentTimestamp - previousClickTimestamp > MINIMUM_INTERVAL)) {
            onDebouncedClick(clickedView);
        }
    }
}
