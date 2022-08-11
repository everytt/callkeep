package io.wazo.callkeep.activity;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import java.util.Calendar;

import io.wazo.callkeep.R;

public class PanelRight extends LinearLayout implements View.OnTouchListener {
    private View mContainer;
    private ImageView mHandle;
    private ImageView mContent;

    private float mStartHandleX = -100;
    private float mStartContentX = -100;
    private float cX;
    private float dX;

    private OnPanelListener mListener;

    public PanelRight(Context context) {
        super(context);
        init();
    }

    public PanelRight(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PanelRight(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        inflate(getContext(), R.layout.view_panel_right, this);

        mContainer = findViewById(R.id.letf_container);
        mHandle = (ImageView) findViewById(R.id.handler);
        mContent = (ImageView) findViewById(R.id.content);

        mHandle.setOnTouchListener(this);
    }

    public void setOnPanelListener(OnPanelListener listener) {
        mListener = listener;
    }

    private long mPreTime;

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        switch (motionEvent.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (mStartHandleX == -100) {
                    mStartHandleX = view.getX();
                }

                if (mStartContentX == -100) {
                    mStartContentX = mContent.getX();
                }

                dX = view.getX() - motionEvent.getRawX();
                cX = mContent.getX() - motionEvent.getRawX();

                mPreTime = Calendar.getInstance().getTimeInMillis();
                break;

            case MotionEvent.ACTION_MOVE:
                float afterX = motionEvent.getRawX() + dX;
                if (afterX > 0 && (afterX + view.getWidth()) < mContainer.getWidth()) {
                    view.animate().x(motionEvent.getRawX() + dX).setDuration(0).start();
                    mContent.animate().x(motionEvent.getRawX() + cX).setDuration(0).start();
                } else {
                    if ((afterX + view.getWidth()) >= mContainer.getWidth()) {
                        float x = mContainer.getWidth() - view.getWidth();
                        view.animate().x(x).setDuration(50).start();
                        mContent.animate().x(mContent.getX() + x - view.getX()).setDuration(50).start();
                        return true;
                    }
                    return false;
                }
                break;
            case MotionEvent.ACTION_UP:
                if (Calendar.getInstance().getTimeInMillis() - mPreTime > 100) {
                    if (mListener != null) {
                        mListener.onPanelEnd();
                    }
                } else {
                    reset();
                }
                break;
            default:
                return false;
        }
        return true;
    }


    private void reset() {
        mHandle.animate()
                .x(mStartHandleX)
                .setDuration(100)
                .start();

        mContent.animate()
                .x(mStartContentX)
                .setDuration(100)
                .start();
    }

    public interface OnPanelListener {
        void onPanelEnd();
    }
}
