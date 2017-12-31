package me.wizos.loread.activity;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.KeyEvent;

import com.socks.library.KLog;

import me.wizos.loread.App;
import me.wizos.loread.R;
import me.wizos.loread.data.WithSet;
import me.wizos.loread.view.colorful.Colorful;

/**
 * Created by Wizos on 2016/3/12.
 */
public abstract class BaseActivity extends AppCompatActivity {
    private static String TAG = "";
    /*
     * LOG打印标签
     * getClass()获得当前对象的类型
     * java中有Class类,用以描述类型信息.
     * 如用语句 Class theClass="hello".getClass();
     * 得到的就是字符串的类型.getSimpleName()返回源代码中给出的底层类的简称。
     */

    /*
     * http://blog.csdn.net/sinat_31311947/article/details/50619467
     * 在实例子类的时候，如果父类的构造器中使用了this，其实这个this也还是指向子类这个this。
     */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showCurrentTheme();
//        App.i().addActivity(this);
    }


    protected void goTo(String toActivity) {
        Intent intent;
//        intent.putExtra("goToCode",notifyChange); // 这个似乎基本没有地方用到
        if (TAG.equals(toActivity)) {
            KLog.i(this.toString() + "【跳转无效，为当前页】");
            return;
        }else if(toActivity.equals(MainActivity.TAG)){
            intent = new Intent(this, MainActivity.class);
        }else if(toActivity.equals(LoginActivity.TAG)){
            intent = new Intent(this, LoginActivity.class);
//        }else if(toActivity.equals(TagActivity.TAG)){
//            intent = new Intent(this, TagActivity.class);
        }else if(toActivity.equals(ArticleActivity.TAG)){
            intent = new Intent(this, ArticleActivity.class);
        }else {
            return;
        }
        startActivity(intent);
    }


    protected Colorful mColorful;

    /**
     * 设置各个视图与颜色属性的关联
     */

    // 自动设置当前主题
    protected void showCurrentTheme() {
        Colorful.Builder mColorfulBuilder = new Colorful.Builder(this);
        mColorful = buildColorful(mColorfulBuilder).create();
        if (WithSet.i().getThemeMode() == App.theme_Day) {
            mColorful.setTheme(R.style.AppTheme_Day);
        } else {
            mColorful.setTheme(R.style.AppTheme_Night);
        }
    }

    protected abstract Colorful.Builder buildColorful(Colorful.Builder mColorfulBuilder);

    /**
     * 手动切换主题并保存
     */
    protected void manualToggleTheme() {
        if (WithSet.i().getThemeMode() == App.theme_Day) {
            mColorful.setTheme(R.style.AppTheme_Night);
            WithSet.i().setThemeMode(App.theme_Night);
        } else {
            mColorful.setTheme(R.style.AppTheme_Day);
            WithSet.i().setThemeMode(App.theme_Day);
        }
    }


    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) { // 后者为短期内按下的次数
//            App.i().finishActivity(this);// 移除这个 Activity
            this.finish();
            return true;//返回真表示返回键被屏蔽掉
        }
        return super.onKeyDown(keyCode, event);
    }


}
