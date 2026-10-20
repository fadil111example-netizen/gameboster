package com.gamebooster.app;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

public final class Anim {
    private Anim() {}

    /** Efek tekan: mengecil saat disentuh, memantul saat dilepas. Klik tetap berjalan normal. */
    public static void press(View... views) {
        for (View v : views) {
            v.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View view, MotionEvent ev) {
                    switch (ev.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            view.animate().scaleX(0.94f).scaleY(0.94f).setDuration(90).start();
                            break;
                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            view.animate().scaleX(1f).scaleY(1f).setDuration(220)
                                    .setInterpolator(new OvershootInterpolator(2f)).start();
                            break;
                        default:
                            break;
                    }
                    return false;
                }
            });
        }
    }

    /** Gerakan melayang pelan bolak-balik (orb latar). Hentikan dengan cancel() di onDestroy. */
    public static ObjectAnimator drift(View v, float dx, float dy, long duration) {
        ObjectAnimator a = ObjectAnimator.ofPropertyValuesHolder(v,
                PropertyValuesHolder.ofFloat(View.TRANSLATION_X, -dx, dx),
                PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, -dy, dy));
        a.setDuration(duration);
        a.setRepeatCount(ObjectAnimator.INFINITE);
        a.setRepeatMode(ObjectAnimator.REVERSE);
        a.setInterpolator(new AccelerateDecelerateInterpolator());
        a.start();
        return a;
    }

    /** Cincin denyut (glow) di belakang tombol. Hentikan dengan cancel() di onDestroy. */
    public static ObjectAnimator pulse(View v, long delay, float sx, float sy) {
        ObjectAnimator a = ObjectAnimator.ofPropertyValuesHolder(v,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, sx),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, sy),
                PropertyValuesHolder.ofFloat(View.ALPHA, 0.75f, 0f));
        a.setDuration(1800);
        a.setStartDelay(delay);
        a.setRepeatCount(ObjectAnimator.INFINITE);
        a.start();
        return a;
    }
}
