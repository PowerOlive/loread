package me.wizos.loread;

import android.app.Application;
import android.content.MutableContextWrapper;
import android.os.Handler;
import android.support.v4.util.ArrayMap;
import android.text.TextUtils;

import com.bumptech.glide.Glide;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.https.HttpsUtils;
import com.socks.library.KLog;
import com.tencent.bugly.crashreport.CrashReport;

import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import me.wizos.loread.bean.config.FeedConfig;
import me.wizos.loread.data.WithDB;
import me.wizos.loread.data.WithPref;
import me.wizos.loread.db.Article;
import me.wizos.loread.db.Feed;
import me.wizos.loread.db.Tag;
import me.wizos.loread.db.dao.DaoMaster;
import me.wizos.loread.db.dao.DaoSession;
import me.wizos.loread.db.dao.SQLiteOpenHelperS;
import me.wizos.loread.event.Sync;
import me.wizos.loread.net.Api;
import me.wizos.loread.net.InoApi;
import me.wizos.loread.utils.FileUtil;
import me.wizos.loread.utils.TimeUtil;
import me.wizos.loread.view.WebViewS;
import okhttp3.OkHttpClient;

//import com.squareup.leakcanary.LeakCanary;

/**
 * 在Android中，可以通过继承Application类来实现应用程序级的全局变量，这种全局变量方法相对静态类更有保障，直到应用的所有Activity全部被destory掉之后才会被释放掉。
 *
 * Created by Wizos on 2015/12/24.
 * */
public class App extends Application{
    /**
     * 此处的单例不会造成内存泄露，因为 App 本身就是全局的单例
     */
    public static App instance;
    public final static String APP_NAME_EN = "loread";
    public final static String DB_NAME = "loread_DB";
    public final static int Theme_Day = 0;
    public final static int Theme_Night = 1;
    public static ArrayMap<String, FeedConfig> feedsConfigMap = new ArrayMap<>();

    // 跟使用的 API 有关的 字段
    public static long UserID;
    public static String StreamId;
    public static String StreamTitle;
    public static String StreamState;
    // 这个只是从 Read 属性的4个类型(Readed, UnRead, UnReading, All), Star 属性的3个类型(Stared, UnStar, All)中，生硬的抽出 UnRead(含UnReading), Stared, All 3个快捷状态，供用户在主页面切换时使用
    // 由于根据 StreamId 来获取文章，可从2个属性( Categories[针对Tag], OriginStreamId[针对Feed] )上，共4个变化上（All, Tag, NoTag, Feed）来获取文章。
    // 根据 StreamState 也是从2个属性(ReadState, StarState)的3个快捷状态 ( UnRead[含UnReading], Stared, All ) 来获取文章。
    // 所以文章列表页会有6种组合：某个 Categories 内的 UnRead[含UnReading], Stared, All。某个 OriginStreamId 内的 UnRead[含UnReading], Stared, All。
    // 所有定下来去获取文章的函数也有6个：getArtsUnreadInTag(), getArtsStaredInTag(), getArtsAllInTag(),getUnreadArtsInFeed(), getStaredArtsInFeed(), getAllArtsInFeed()

    public static List<Article> articleList = new ArrayList<>();
    public static List<Tag> tagList = new ArrayList<>();

    public static Handler artHandler;
    public boolean isSyncing = false;
    public static boolean isSyncingUnreadCount = false;


    public static String cacheRelativePath, boxRelativePath, storeRelativePath;
    public static String externalFilesDir;
    public static String externalCachesDir;
    public static String webViewBaseUrl;
    public static OkHttpClient imgHttpClient;
//    public static String boxReadRelativePath, storeReadRelativePath;
//    public static String logRelativePath,logAbsolutePath;

    private static DaoSession daoSession;

    public static App i() {
        return instance;
    }


    /**
     * 首次打开 ArticleActivity 时，将生成的 WebView 缓存在这里，从而再次打开 ArticleActivity 时，加快文章的渲染速度，效果明显。
     */
    public List<WebViewS> mWebViewCaches = new ArrayList<>();

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
//        DBHelper.startUpgrade(this);
        // 提前初始化
        WithDB.i();
        initVar();
        initApiConfig();
        initFeedsConfig();
        initAutoToggleTheme();
        initLogAndCrash();
        InoApi.i().initAuthHeaders();
        // 初始化网络框架
        OkGo.getInstance().init(this);
        buildImgClient();

        initUnreadCount();
        initFeedsCategoryid();

        // 链接：https://www.jianshu.com/p/fc7909e24178
        // 第一次打开 Web 页面 ， 使用 WebView 加载页面的时候特别慢 ，第二次打开就能明显的感觉到速度有提升 ，为什么 ？
        // 是因为在你第一次加载页面的时候 WebView 内核并没有初始化 ， 所以在第一次加载页面的时候需要耗时去初始化 WebView 内核 。
        // 提前初始化 WebView 内核 ，例如如下把它放到了 Application 里面去初始化 , 在页面里可以直接使用该 WebView
        // 但是这里会影响，从 webview 中打开对话框
        mWebViewCaches.add(new WebViewS(new MutableContextWrapper(this)));
        mWebViewCaches.add(new WebViewS(new MutableContextWrapper(this)));
        mWebViewCaches.add(new WebViewS(new MutableContextWrapper(this)));


//        JobManager.create(this).addJobCreator(new JobCreateRouter());
//        JobManager.instance().cancelAllForTag("job_sync");
        /*
         * 当我们在 debug 的时候，往往会把间隔时间调短从而可以马上看到效果。
         * 但是在 Android N 中，规定了定时任务间隔最少为 15 分钟，如果小于 15 分钟会得到一个错误：intervalMs is out of range
         * 这时，可以调用 JobConfig 的 setAllowSmallerIntervalsForMarshmallow(true) 方法在 debug 模式下避免这个问题。
         * 但在正式环境下一定要注意间隔时间设置为 15 分钟以上。
         */
//        JobConfig.setAllowSmallerIntervalsForMarshmallow(true);
    }


    /**
     * 程序在内存清理的时候执行
     */
    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level == TRIM_MEMORY_UI_HIDDEN) {
            Glide.get(this).clearMemory();
        }
        Glide.get(this).trimMemory(level);
    }

    /**
     * 内存低的时候执行
     */
    @Override
    public void onLowMemory() {
        super.onLowMemory();
        // 清理 Glide 的缓存
        Glide.get(this).clearMemory();
    }

    /**
     * 程序终止的时候执行
     */
    @Override
    public void onTerminate() {
        KLog.i("程序终止的时候执行");
//        JobManager.instance().cancelAll();
        super.onTerminate();
    }

    public void updateArtList(List<Article> temps) {
        articleList.clear();
        articleList.addAll(temps);
    }

    public void updateTagList(List<Tag> temps) {
        tagList.clear();
        tagList.addAll(temps);
    }

    private void initLogAndCrash() {
        CrashReport.setIsDevelopmentDevice(this, BuildConfig.DEBUG);
        if (BuildConfig.DEBUG) {
            // 测试的时候设为 true
            CrashReport.initCrashReport(App.i(), "900044326", true);
        } else {
            CrashReport.initCrashReport(App.i(), "900044326", false);
        }
        if (BuildConfig.DEBUG) {
            KLog.init(true);
        } else {
            KLog.init(false);
        }
    }


//  内存泄漏检测工具
//    private void initLeakCanary() {
//        if (LeakCanary.isInAnalyzerProcess(this)) {
//            return;
//        }
//        LeakCanary.install(this);
//    }


    public void readHost() {
        if (!WithPref.i().isInoreaderProxy()) {
            Api.HOST = InoApi.HOST;
        }else {
            Api.HOST = WithPref.i().getInoreaderProxyHost();
        }
    }

    private void initVar() {
        externalFilesDir = getExternalFilesDir(null) + "";
        cacheRelativePath = FileUtil.getRelativeDir(Api.SAVE_DIR_CACHE);
        boxRelativePath = FileUtil.getRelativeDir(Api.SAVE_DIR_BOX);
        storeRelativePath = FileUtil.getRelativeDir(Api.SAVE_DIR_STORE);
        webViewBaseUrl = "file://" + externalFilesDir + "/cache/";
//        cacheAbsolutePath = FileUtil.getAbsoluteDir(Api.SAVE_DIR_CACHE); // 仅在储存于 html 时使用
//        boxAbsolutePath = FileUtil.getAbsoluteDir(Api.SAVE_DIR_BOX);
//        storeAbsolutePath = FileUtil.getAbsoluteDir(Api.SAVE_DIR_STORE);

//        logRelativePath = FileUtil.getRelativeDir("log");
//        logAbsolutePath = FileUtil.getAbsoluteDir("log");

//        boxReadRelativePath = FileUtil.getRelativeDir("boxRead");
//        storeReadRelativePath = FileUtil.getRelativeDir("storeRead");
//        boxReadAbsolutePath = FileUtil.getAbsoluteDir( "boxRead" );
//        storeReadAbsolutePath = FileUtil.getAbsoluteDir( "storeRead" );
    }

    private void initApiConfig() {
        // 读取当前的API

        // 读取代理配置
//        readHost();
        // 读取验证
        InoApi.INOREADER_ATUH = WithPref.i().getAuth();
        // 读取uid
        UserID = WithPref.i().getUseId();
        StreamState = WithPref.i().getStreamState();
        StreamId = WithPref.i().getStreamId();
        KLog.e(StreamState + "  " + StreamId + "  ");
        if (StreamId == null || StreamId.equals("")) {
            StreamId = "user/" + UserID + Api.U_READING_LIST;
        }
        if (StreamId.equals("user/" + UserID + Api.U_READING_LIST)) {
            StreamTitle = getString(R.string.main_activity_title_all);
        } else if (StreamId.equals("user/" + UserID + Api.U_NO_LABEL)) {
            StreamTitle = getString(R.string.main_activity_title_untag);
        } else if (StreamId.startsWith("user/")) {
            try {
                StreamTitle = WithDB.i().getTag(StreamId).getTitle();
            } catch (Exception e) {
                StreamId = "user/" + UserID + Api.U_READING_LIST;
                StreamTitle = getString(R.string.main_activity_title_all);
            }
        } else {
            try {
                StreamTitle = WithDB.i().getFeed(StreamId).getTitle();
            } catch (Exception e) {
                StreamId = "user/" + UserID + Api.U_READING_LIST;
                StreamTitle = getString(R.string.main_activity_title_all);
            }
        }

        KLog.e("此时StreamId为：" + StreamId + "   此时 Title 为：" + StreamTitle);
    }


    public static ConcurrentHashMap<String, Integer> unreadCountMap = new ConcurrentHashMap<>();
    public static ArrayMap<String, Integer> unreadOffsetMap;
    public static ConcurrentHashMap<String, String> feedsCategoryIdMap = new ConcurrentHashMap<>();

    private void initUnreadCount() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (TextUtils.isEmpty(WithPref.i().getAuth())) {
                    return;
                }

                unreadCountMap.put(getRootTagId(), WithDB.i().getUnreadArtsCount());
                unreadCountMap.put(getUnclassifiedTagId(), WithDB.i().getUnreadArtsCountNoTag());

                List<Tag> tags = WithDB.i().getTags();
                for (int i = 0, size = tags.size(); i < size; i++) {
                    unreadCountMap.put(tags.get(i).getId(), WithDB.i().getUnreadArtsCountByTag(tags.get(i)));
                }

                List<Feed> feeds = WithDB.i().getFeeds();
                for (int i = 0, size = feeds.size(); i < size; i++) {
                    unreadCountMap.put(feeds.get(i).getId(), WithDB.i().getUnreadArtsCountByFeed(feeds.get(i).getId()));
                }
            }
        }).start();
    }

    public void initFeedsCategoryid() {
        feedsCategoryIdMap = new ConcurrentHashMap<>();
        List<Feed> feeds = WithDB.i().getFeeds();
        for (int i = 0, size = feeds.size(); i < size; i++) {
            feedsCategoryIdMap.put(feeds.get(i).getId(), feeds.get(i).getCategoryid());
        }
    }


    private String getUnclassifiedTagId() {
        return "user/" + WithPref.i().getUseId() + Api.U_NO_LABEL;
    }

    private String getRootTagId() {
        return "user/" + WithPref.i().getUseId() + Api.U_READING_LIST;
    }


    public void initFeedsConfig() {
        Gson gson = new Gson();
        String feedsConfig;

        feedsConfig = FileUtil.readFile(externalFilesDir + "/config/feeds-config.json");

        feedsConfigMap = gson.fromJson(feedsConfig, new TypeToken<ArrayMap<String, FeedConfig>>() {
        }.getType());
        if (feedsConfigMap == null) {
            feedsConfigMap = new ArrayMap<>();
        }
//        String referers = FileUtil.readFile(getExternalFilesDir(null) + "/config/referers.json" );
//        feedsConfigMap = gson.fromJson(referers, ArrayMap.class);
    }

    public void saveFeedsConfig() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                FileUtil.saveFile(getExternalFilesDir(null) + "/config/feeds-config.json", new Gson().toJson(feedsConfigMap));
            }
        }).start();
    }

//    private ArrayMap<String,String> feedsReferer = new ArrayMap<>();
//    private ArrayMap<String,String> feedsOpenMode = new ArrayMap<>();
//    private ArrayMap<String,String> feedsUserAgent = new ArrayMap<>();
//    public void initFeedsConfig2(){
//        Gson gson = new Gson();
//        String feedsRefererContent = FileUtil.readFile(getExternalFilesDir(null) + "/config/feeds-referer.json" );
//        feedsReferer = gson.fromJson(feedsRefererContent, new TypeToken<ArrayMap<String,String>>() {}.getType());
//
//        String feedsOpenModeContent = FileUtil.readFile(getExternalFilesDir(null) + "/config/feeds-open-mode.json" );
//        feedsOpenMode = gson.fromJson(feedsOpenModeContent, new TypeToken<ArrayMap<String,String>>() {}.getType());
//
//        String feedsUserAgentContent = FileUtil.readFile(getExternalFilesDir(null) + "/config/feeds-user-agent.json" );
//        feedsUserAgent = gson.fromJson(feedsUserAgentContent, new TypeToken<ArrayMap<String,String>>() {}.getType());
//    }



    public void clearApiData() {
        WithPref.i().clear();
        WithDB.i().clear();
        FileUtil.deleteHtmlDir(new File(App.cacheRelativePath));
        OkGo.getInstance().cancelAll();
        EventBus.getDefault().post(new Sync(Sync.STOP));
    }


    public static boolean hadAutoToggleTheme = false;

    protected void initAutoToggleTheme() {
        KLog.e(" 初始化主题" + WithPref.i().isAutoToggleTheme() + TimeUtil.getCurrentHour());
        if (!WithPref.i().isAutoToggleTheme()) {
            return;
        }
        int hour = TimeUtil.getCurrentHour();
        int lastThemeMode = WithPref.i().getThemeMode();
        if (hour >= 7 && hour < 20) {
            WithPref.i().setThemeMode(App.Theme_Day);
        } else {
            WithPref.i().setThemeMode(App.Theme_Night);
        }
        if (WithPref.i().getThemeMode() != lastThemeMode) {
            hadAutoToggleTheme = true;
        }
    }

    /**
     * 手动去指定夜间时间意义不大，可以不做，只会徒增系统复杂性而已
     */
//    protected void initAutoToggleTheme2() {
//        KLog.e(" 初始化主题" + WithPref.i().isAutoToggleTheme() + TimeUtil.getCurrentHour() );
//        if (!WithPref.i().isAutoToggleTheme()) {
//            return;
//        }
//
//        int lastThemeMode = WithPref.i().getThemeMode();
//        String now = TimeUtil.getNow();
//        if(TimeUtil.compare(now,WithPref.i().getNightThemeStartTime()) || TimeUtil.compare(now,WithPref.i().getNightThemeEndTime()) ){
//            WithPref.i().setThemeMode(App.Theme_Night);
//        }else {
//            WithPref.i().setThemeMode(App.Theme_Day);
//        }
//
//        if(WithPref.i().getThemeMode() != lastThemeMode){
//            hadAutoToggleTheme = true;
//        }
//    }


    // 官方推荐将获取 DaoMaster 对象的方法放到 Application 层，这样将避免多次创建生成 Session 对象
    public DaoSession getDaoSession() {
        if (daoSession == null) {
//            DaoMaster.OpenHelper helper = new DaoMaster.DevOpenHelper(i(), DB_NAME, null);
            // 此处是为了方便升级
            SQLiteOpenHelperS helper = new SQLiteOpenHelperS(i(), DB_NAME, null);
            daoSession = new DaoMaster(helper.getWritableDb()).newSession();
//            // 注意：默认的 DaoMaster.DevOpenHelper 会在数据库升级时，删除所有的表，意味着这将导致数据的丢失。
//            // 所以，在正式的项目中，你还应该做一层封装，来实现数据库的安全升级。
//            DaoMaster.DevOpenHelper helper = new DaoMaster.DevOpenHelper(this, DB_NAME, null);
//            db = helper.getWritableDatabase();
//            // 注意：该数据库连接属于 DaoMaster，所以多个 Session 指的是相同的数据库连接。
//            daoMaster = new DaoMaster(db);
//            daoSession = daoMaster.newSession();
        }
        return daoSession;
    }


    public void buildImgClient() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        builder.readTimeout(60000L, TimeUnit.MILLISECONDS);
        builder.writeTimeout(60000L, TimeUnit.MILLISECONDS);
        builder.connectTimeout(30000L, TimeUnit.MILLISECONDS);
        HttpsUtils.SSLParams sslParams = HttpsUtils.getSslSocketFactory();
        builder.sslSocketFactory(sslParams.sSLSocketFactory, sslParams.trustManager);
        builder.hostnameVerifier(HttpsUtils.UnSafeHostnameVerifier);
        imgHttpClient = builder.build();
    }

}
