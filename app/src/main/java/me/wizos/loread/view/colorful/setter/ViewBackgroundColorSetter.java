package me.wizos.loread.view.colorful.setter;

import android.content.res.Resources.Theme;
import android.view.View;

/**
 * View的背景色Setter
 *
 * @author mrsimple
 */
public class ViewBackgroundColorSetter extends ViewSetter {

    public ViewBackgroundColorSetter(View target, int resId) {
        super(target, resId);
    }

    public ViewBackgroundColorSetter(int viewId, int resId) {
        super(viewId, resId);
    }

    @Override
    public void setValue(Theme newTheme, int themeId) {
        if (mView != null) {
            int alpha = 255;
            if (mView.getBackground() != null) {
                alpha = mView.getBackground().getAlpha();// 自加。保留透明度信息。
            }
            mView.setBackgroundColor(getColor(newTheme));
            mView.getBackground().setAlpha(alpha);// 自加。保留透明度信息。
        }
    }

}
