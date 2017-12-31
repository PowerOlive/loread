package me.wizos.loread.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.os.Build;

import com.socks.library.KLog;

import me.wizos.loread.App;
import me.wizos.loread.R;
import me.wizos.loread.data.WithSet;



/**
 * Created by mummyding on 15-11-22.<br>
 * Utils for Post Data via network and get network state of cell phone.
 * @author MummyDing
 * @version Leisure 1.0
 */
public class HttpUtil {
//    private static final OkHttpClient mOkHttpClient = new OkHttpClient();
//    private static final Dispatcher dispatcher = new Dispatcher();

    static{
        KLog.e("又来了一个Http链接，当前线程为：" + Thread.currentThread() + "。当前实例为");
//        dispatcher.setMaxRequests(3);
//        dispatcher.setMaxRequestsPerHost(3);
//        mOkHttpClient.setDispatcher(dispatcher);
//        mOkHttpClient.setConnectTimeout(30, TimeUnit.SECONDS); // 初始为30
//        mOkHttpClient.setReadTimeout(60, TimeUnit.SECONDS);// 初始为30
//        mOkHttpClient.setWriteTimeout(60, TimeUnit.SECONDS);// 初始为30
    }

    /**
     * 开启异步线程访问网络
     * @param request
     * @param responseCallback
     */
//    public static void enqueue(Request request, Callback responseCallback){
//        mOkHttpClient.newCall(request).enqueue(responseCallback);
//    }

//    private static OkHttpClient imgOkHttpClient;
    private static HttpUtil httpUtil;

    public static HttpUtil i() {
        if (httpUtil == null) { // 双重锁定，只有在 withDB 还没被初始化的时候才会进入到下一行，然后加上同步锁
            synchronized (HttpUtil.class) { // 同步锁，避免多线程时可能 new 出两个实例的情况
                if (httpUtil == null) {
                    // All init here
                    httpUtil = new HttpUtil();
//                    imgOkHttpClient = new OkHttpClient();
//                    Dispatcher dispatcher = new Dispatcher();
//                    dispatcher.setMaxRequests(3);
//                    dispatcher.setMaxRequestsPerHost(3);
//
//                    imgOkHttpClient.setDispatcher(dispatcher);
//                    imgOkHttpClient.setConnectTimeout(30, TimeUnit.SECONDS); // 初始为30
//                    imgOkHttpClient.setReadTimeout(60, TimeUnit.SECONDS);// 初始为30
//                    imgOkHttpClient.setWriteTimeout(60, TimeUnit.SECONDS);// 初始为30
                }
            }
        }
        return httpUtil;
    }
//    public void exe( Request request, Callback responseCallback ){
//        imgOkHttpClient.newCall(request).enqueue(responseCallback);
//    }


    /**
     * 能否下载图片分以下几种情况：
     * 1，开启省流量 & Wifi 可用
     * 2，开启省流量 & Wifi 不可用
     * 3，关闭省流量 & 网络 可用
     * 4，关闭省流量 & 网络 不可用
     *
     * @return
     */
    public static boolean canDownImg() {
        if (WithSet.i().isDownImgWifi() && !isWiFiActive()) {
            ToastUtil.showShort(App.i().getString(R.string.toast_not_wifi_mode));
            return false;
        }
        if (!WithSet.i().isDownImgWifi() && !isNetworkAvailable()) {
            ToastUtil.showShort(App.i().getString(R.string.toast_not_network));
            return false;
        }
        return true;
    }

    /**
     * 判断网络是否可用
     */
    public static boolean isNetworkAvailable() {
        ConnectivityManager connectivity = (ConnectivityManager) App.i().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = connectivity.getActiveNetworkInfo();
        return (info != null && info.isConnected());
    }

    /**
     * 判断WIFI是否可用
     */
    public static boolean isWiFiActive() {
        ConnectivityManager connectivity = (ConnectivityManager) App.i().getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivity == null) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= 23) {
            KLog.d("Wifi", ">=23");
            Network[] networks = connectivity.getAllNetworks();
            if (networks == null) {
                return false;
            }
            NetworkInfo networkInfo;
            for (Network network : networks) {
                networkInfo = connectivity.getNetworkInfo(network);
//                return wifiNetworkIsAvailable( networkInfo );
                if (networkInfo.getType() != ConnectivityManager.TYPE_WIFI) continue;
                KLog.e("Wifi==", networkInfo.isConnected());
                return networkInfo.isConnected();
            }
        } else {
            KLog.d("Wifi", "<23");
            NetworkInfo[] networkInfos = connectivity.getAllNetworkInfo();
            if (networkInfos == null) {
                return false;
            }
            for (NetworkInfo networkInfo : networkInfos) {
                //此处请务必使用NetworkInfo对象下的isAvailable（）方法，isConnected()是检测当前是否连接到了wifi
                if (networkInfo.getType() != ConnectivityManager.TYPE_WIFI) continue;
                KLog.i("Wifi==", networkInfo.isConnected());
                return networkInfo.isConnected();
            }
        }
        return true;
    }

}