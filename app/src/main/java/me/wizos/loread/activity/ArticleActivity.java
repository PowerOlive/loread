package me.wizos.loread.activity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.util.ArrayMap;
import android.support.v4.widget.NestedScrollView;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.text.Spanned;
import android.util.SparseIntArray;
import android.view.View;
import android.view.ViewConfiguration;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.afollestad.materialdialogs.MaterialDialog;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.socks.library.KLog;

import java.io.File;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import me.wizos.loread.App;
import me.wizos.loread.R;
import me.wizos.loread.bean.Article;
import me.wizos.loread.bean.Tag;
import me.wizos.loread.bean.gson.ExtraImg;
import me.wizos.loread.bean.gson.SrcPair;
import me.wizos.loread.data.WithDB;
import me.wizos.loread.data.WithSet;
import me.wizos.loread.net.API;
import me.wizos.loread.net.Neter;
import me.wizos.loread.net.Parser;
import me.wizos.loread.presenter.adapter.MaterialSimpleListAdapter;
import me.wizos.loread.presenter.adapter.MaterialSimpleListItem;
import me.wizos.loread.utils.HttpUtil;
import me.wizos.loread.utils.UFile;
import me.wizos.loread.utils.UString;
import me.wizos.loread.utils.UTime;
import me.wizos.loread.utils.UToast;
import me.wizos.loread.utils.colorful.Colorful;
import me.wizos.loread.view.IconFontView;

@SuppressLint("SetJavaScriptEnabled")
public class ArticleActivity extends BaseActivity {
    protected static final String TAG = "ArticleActivityView";
    private WebView webView; // implements Html.ImageGetter
    private LinearLayout mll;
    protected Context context;
    protected Neter mNeter;
//    protected Parser mParser;
    protected TextView vTitle ,vDate ,vTime, vFeed;
    protected IconFontView vStar , vRead;
    protected NestedScrollView vScrolllayout ;
    protected TextView vArticleNum;

    private int numOfImgs,numOfGetImgs = 0 ,numOfFailureImg = 0 ,numOfFailure = 0 ,numOfFailures = 4;
    private Article article;
    private int articleNo, articleCount;
    private String imgState; // 根据此值可以判断文章是否有被打开：null = 未打开；"" = 无图；其他为有图，图的信息在 extraImg中
    private ExtraImg extraImg;
    private String showContent = "";
    private String articleID = "";
    private String sReadState = "";
    private String sStarState = "";
    private String htmlState = "";
    private String fileNameInMD5 = "";
    private ArrayMap<Integer,SrcPair> lossSrcList, obtainSrcList ;
    private SparseIntArray failImgList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_article);
        context = this;
        App.addActivity(this);
        mNeter = new Neter(handler);
//        mNeter.setLogRequestListener(this);
//        mParser = new Parser();
        initColorful();
        initView();
        initData();
    }

    protected void initColorful(){
        mColorful = new Colorful.Builder(this)
//                .backgroundDrawable(R.id.swipe_layout, R.attr.root_view_bg)
                // 设置view的背景图片
                .backgroundColor(R.id.art_coordinator, R.attr.root_view_bg)
                // 设置 toolbar
                .backgroundColor(R.id.art_toolbar, R.attr.topbar_bg)
                .textColor(R.id.art_toolbar_num, R.attr.topbar_fg)
                // 设置文章信息
                .textColor(R.id.art_title, R.attr.art_title)
                .textColor(R.id.art_feed, R.attr.art_feed)
                .textColor(R.id.art_date, R.attr.art_date)
                .textColor(R.id.art_time, R.attr.art_time)

                // 设置 bottombar
                .backgroundColor(R.id.art_bottombar, R.attr.bottombar_bg)
                .textColor(R.id.art_bottombar_read, R.attr.bottombar_fg)
                .textColor(R.id.art_bottombar_star, R.attr.bottombar_fg)
                .textColor(R.id.art_bottombar_label, R.attr.bottombar_fg)
                .textColor(R.id.art_bottombar_save, R.attr.bottombar_fg)

                .create(); // 创建Colorful对象
        autoToggleThemeSetting();
    }

    @Override
    protected void onResume(){
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 如果参数为null的话，会将所有的Callbacks和Messages全部清除掉。
        // 这样做的好处是在Acticity退出的时候，可以避免内存泄露。因为 handler 内可能引用 Activity ，导致 Activity 退出后，内存泄漏
        mHandler.removeCallbacksAndMessages(null);
        handler.removeCallbacksAndMessages(null);
//        article = null;
        webView.removeAllViews();
        webView.destroy();
        mll.removeView(webView);
    }


    @Override
    protected Context getActivity(){
        return context;
    }
    public String getTAG(){
        return TAG;
    }

    private void initView() {
        initToolbar();
        initWebView();
        vTitle = (TextView) findViewById(R.id.art_title);
        vDate = (TextView) findViewById(R.id.art_date);
//        vTime = (TextView) findViewById(R.id.art_time);
        vFeed = (TextView) findViewById(R.id.art_feed);
        vStar = (IconFontView) findViewById(R.id.art_bottombar_star);
        vRead = (IconFontView) findViewById(R.id.art_bottombar_read);
        vArticleNum =  (TextView)findViewById(R.id.art_toolbar_num);
        vScrolllayout = (NestedScrollView) findViewById(R.id.art_scroll);
    }
    private void initToolbar() {
        Toolbar toolbar = (Toolbar) findViewById(R.id.art_toolbar);
        setSupportActionBar(toolbar); // ActionBar
        getSupportActionBar().setHomeButtonEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowTitleEnabled(false);
        toolbar.setOnClickListener(this);
//        白色箭头
//        Drawable upArrow = getResources().getDrawable(R.drawable.mz_ic_sb_back);
//        upArrow.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_ATOP);
//        getSupportActionBar().setHomeAsUpIndicator(upArrow); // 替换返回箭头
    }

    private void initWebView(){
//        webView = (WebView) findViewById(R.id.article_content);
        webView = new WebView( getApplicationContext() );
        mll = (LinearLayout) findViewById(R.id.article_webview);
        mll.addView(webView);
        WebSettings webSettings = webView.getSettings();
//        webSettings.setCacheMode(WebSettings.LOAD_NO_CACHE); // 默认不使用缓存
        webSettings.setUseWideViewPort(false);// 设置此属性，可任意比例缩放
        webSettings.setDisplayZoomControls(false); //隐藏webview缩放按钮
        webSettings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.SINGLE_COLUMN); // 就是这句使自适应屏幕
        webSettings.setLoadWithOverviewMode(true);// 缩放至屏幕的大小
        webSettings.setJavaScriptEnabled(true);
        // 添加js交互接口类，并起别名 imagelistner
        webView.addJavascriptInterface(new JavaScriptInterface(this), "imagelistner");
        webView.setWebViewClient(new MyWebViewClient());
//        setOneapmWebViewWatch();
    }


    private void initData(){
        articleID = getIntent().getExtras().getString("articleID");
        articleNo = getIntent().getExtras().getInt("articleNum"); // 文章在列表中的位置编号
        articleCount = getIntent().getExtras().getInt("articleCount"); // 列表中所有的文章数目

        KLog.d("【article】" + articleID);
        article = WithDB.getInstance().getArticle(articleID);
        if ( article == null ){
            KLog.d("【article为空】");
            // 重新下载
            return;
        }
        sReadState = article.getReadState();
        sStarState = article.getStarState();
//        String articleUrl = article.getCanonical();
        Spanned titleWithUrl = Html.fromHtml("<a href=\"" + article.getCanonical() +"\">" + article.getTitle() + "</a>");
        vTitle.setText( titleWithUrl );
        vDate.setText(UTime.getFormatDate(article.getTimestampUsec()));
        String author = article.getAuthor();
        if (author != null && !author.equals("")) {
            author = article.getOriginTitle() + "@" + article.getAuthor();
        } else {
            author = article.getOriginTitle();
        }

        vFeed.setText(author);


        fileNameInMD5 = UString.stringToMD5(articleID);
        ArrayList<String> htmlMsg = UFile.readHtml( fileNameInMD5 ,article.getTitle());
        if(htmlMsg != null){
            htmlState = htmlMsg.get(0);
            showContent = htmlMsg.get(1);
        }

//        KLog.d( "【article状态为】" + sReadState + titleWithUrl );
        numOfGetImgs = 0;
        imgState = article.getImgState();// 读取失败 imgSrc 的字段 , 有4类值：
        // 1，null（未打开）；2，"" （无图且被打开）； 3，ok（有图且加载完成）；4，src list (代表要提取正文与srcList加载图片)
        KLog.d( "文章内容：" + showContent.length() );
        if(UString.isBlank(showContent)){
//            KLog.d( "【文章内容被删，再去加载获取内容】" );
            mNeter.postArticleContents(articleID);
        }else {
            if( imgState == null){ // 文章没有被打开过
//                KLog.d( "【imgState为null】");
                showContent = UString.reviseHtmlNoAd(new StringBuilder(showContent)).toString();
                lossSrcList = UString.getListOfSrcAndHtml(showContent, fileNameInMD5);
                if( lossSrcList!= null){
                    showContent = lossSrcList.get(0).getSaveSrc();
                    lossSrcList.remove(0);
                    if( lossSrcList.size()!=0){
                        article.setCoverSrc( lossSrcList.get(1).getSaveSrc());
                        obtainSrcList = new ArrayMap<>(lossSrcList.size());
                    }
                    logImgStatus(ExtraImg.DOWNLOAD_ING);
//                    KLog.d("检测 obtainSrcList " + lossSrcList.size());
                }else {
                    article.setImgState("");
                }
                UFile.saveCacheHtml(fileNameInMD5, showContent);
                WithDB.getInstance().saveArticle(article);
            }else if( !imgState.equals("")){
                Gson gson = new Gson();
                Type type = new TypeToken<ExtraImg>() {}.getType();
                try{
                    extraImg = gson.fromJson(imgState, type);
                    lossSrcList = extraImg.getLossImgs();
                    obtainSrcList = extraImg.getObtainImgs();
//                    KLog.e("重新进入获取到的imgState记录" + imgState + extraImg +  lossSrcList + obtainSrcList);
                }catch (RuntimeException e){
                    imgState = "";
                }
            }

            String script =
                    "<script type=\"text/javascript\">" +
                            "function initImgClick(){" +
                            "var imgList = document.getElementsByTagName(\"img\"); " +
                            "for(var i=0; i<imgList.length; i++) {" +
                            "    imgList[i].no = i;" +
                            "    imgList[i].onclick = function() {" +
                            "        window.imagelistner.listen( this.no, this.src );  " +
                            "    }  " +
                            "}" +
                            "}" +
                            "function initImgPlaceholder(){" +
                            "var imgList = document.getElementsByTagName(\"img\"); " +
                            "for(var i=0; i<imgList.length; i++) {" +
                            "    imgList[i].src = \"file:///android_asset/down.svg\";"+
                            "}" +
                            "}" +
                            "function appointImgPlaceholder(number){" +
                            "var array = number.split(\"_\");" +
                            "var imgList = document.getElementsByTagName(\"img\"); " +
                            "for(var i=0; i<array.length; i++) {" +
                            "    var n = array[i];" +
                            "    imgList[n].src = \"file:///android_asset/down.svg\";"+
                            "}" +
                            "}" +
                            "</script>";
            // 加载内部css样式 placeholder.png

            String cssPath = getExternalFilesDir(null) + File.separator + "config" + File.separator + "article.css";
            if(!UFile.isFileExists(cssPath)){
                cssPath = "file:///android_asset/article.css";
            }
            String cssPathTheme;
            if(WithSet.getInstance().getThemeMode()==WithSet.themeDay){
                cssPathTheme = "file:///android_asset/article_theme_day.css";
            }else {
                cssPathTheme = "file:///android_asset/article_theme_night.css";
            }
            String contentHeader = "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head>" +
                    "<link rel=\"stylesheet\" href=\"" + cssPath +"\" type=\"text/css\"/>" +
                    "<link rel=\"stylesheet\" href=\"" + cssPathTheme + "\" type=\"text/css\"/>" +
                    script +  "</head><body>";
            String contentFooter = "</body></html>";
            showContent = contentHeader + showContent + contentFooter;

//            if (fileNameInMD5!= null){
//                vArticleNum.setText( fileNameInMD5.substring(0,10) ); // FIXME: 2016/5/3 测试
//            }
            String numStr = String.valueOf(articleNo) + " / " + String.valueOf(articleCount);
            vArticleNum.setText(numStr);

            notifyDataChanged();
        }
        initStateView();
    }

    private String getBaseUrl(String htmlState){
        if (htmlState.equals("cache")) {
            return App.cacheAbsolutePath;
        } else if (htmlState.equals("store")) {
            return App.storeAbsolutePath;
        }else if(htmlState.equals("box")){
            return App.boxAbsolutePath;
        }
        return null;
    }

    // 注入js函数监听
    private void addImageClickListner() {
        KLog.d("正在加载完成js函数" );
        // 这段js函数的功能就是，遍历所有的img几点，并添加onclick函数，函数的功能是在图片点击的时候调用本地java接口并传递url过去

        webView.loadUrl("javascript:initImgList()");
    }


    // js通信接口
    public class JavaScriptInterface {
        private Context context;
        public JavaScriptInterface(Context context) {
            this.context = context;
        }

        @JavascriptInterface
        public void openImage(String img) {
            KLog.e( img );
//            Intent intent = new Intent();
//            intent.putExtra("image", img);
//            intent.setClass(context, ShowWebImageActivity.class);
//            context.startActivity(intent);
//            KLog.e( img );
        }

        @JavascriptInterface
        public void listen(int imgNo, String src){
            KLog.e( imgNo + " = " +  src );
            if( extraImg.getImgStatus() == ExtraImg.DOWNLOAD_ING){
                KLog.e( " = 图片正在下载中，开始重新下载" );
            }else if( extraImg.getImgStatus() == ExtraImg.DOWNLOAD_OVER ){
                KLog.e( " = 图片下载完成，请选择是重新下载还是打开大图" );
            }
            openImgMenuDialog(imgNo);
            // 下载图片
            // 打开大图
        }
    }

    private void openImgMenuDialog(final int imgNo){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                final MaterialSimpleListAdapter adapter = new MaterialSimpleListAdapter( ArticleActivity.this);
                adapter.add(new MaterialSimpleListItem.Builder(ArticleActivity.this)
                        .content("重新下载")
                        .icon(R.drawable.ic_vector_mark_after)
                        .backgroundColor(Color.WHITE)
                        .build());
                adapter.add(new MaterialSimpleListItem.Builder(ArticleActivity.this)
                        .content("打开大图")
                        .icon(R.drawable.ic_vector_mark_before)
                        .backgroundColor(Color.WHITE)
                        .build());
                new MaterialDialog.Builder(ArticleActivity.this)
                        .adapter(adapter, new MaterialDialog.ListCallback() {
                            @Override
                            public void onSelection(MaterialDialog dialog, View itemView, int which, CharSequence text) {
                                switch (which) {
                                    case 0:
                                        KLog.e( "重新下载" + imgNo );
                                        // 此时还要判断他的储存位置 是 box 还是 cache
                                        restartDownloadImg(imgNo);
                                        break;
                                    case 1:
                                        KLog.e( "打开大图" );
                                        break;
                                }

                                dialog.dismiss();
                            }
                        })
                        .show();

            }
        });
    }


    private void restartDownloadImg(int imgNo){
        imgNo = imgNo + 1 ;
        KLog.d(imgNo);
        SrcPair imgSrc;
        if( lossSrcList!=null ){
            imgSrc = lossSrcList.get(imgNo);
            if( imgSrc==null){
                if ( obtainSrcList != null ){
                    imgSrc = obtainSrcList.get(imgNo);
                    if(imgSrc ==null ){
                        UToast.showShort("没有找到图片1");
                        return;
                    }
                }else {
                    UToast.showShort("没有找到图片2");
                    return;
                }
            }
        }else if( obtainSrcList!= null ){
            imgSrc = obtainSrcList.get(imgNo);
            if ( imgSrc==null ){
                UToast.showShort("没有找到图片3");
                return;
            }
        }else {
            UToast.showShort("lossSrcList与obtainSrcList都为null");
            KLog.d("--");
            return;
        }

        if (!HttpUtil.isWifiEnabled()) {
            handler.sendEmptyMessage(API.F_Request);
            return ;}
        String srcBaseUrl = "";
        switch (htmlState){
            case "cache":
                srcBaseUrl = App.cacheRelativePath + fileNameInMD5 + "_files" + File.separator;
                break;
            case "store":
                srcBaseUrl = App.storeRelativePath + article.getTitle() + "_files" + File.separator;
                break;
            case "box":
                srcBaseUrl = App.boxRelativePath + article.getTitle() + "_files" + File.separator;
                break;
        }

//        KLog.d("修改后的src保存地址为："  + lossSrcList.size() + imgNo );
//        KLog.d("修改后的src保存地址为："  + srcBaseUrl + UString.getFileNameExtByUrl(imgSrc.getSaveSrc()) );
        mNeter.loadImg(imgSrc.getNetSrc(), srcBaseUrl + UString.getFileNameExtByUrl(imgSrc.getSaveSrc()), imgNo);
    }




//    ArrayList<String> localSrcList = new ArrayList();
    private void replaceSrc(final int imgNo, final String localSrc){
        final int no = imgNo-1; // 因为图片的标号是从 1 开始，而 DOM 中，要从 0 开始。
        KLog.e("替换src" + no + localSrc );
        webView.post(new Runnable() {
            @Override
            public void run() {
                webView.loadUrl("javascript:(function(){" +
                        "imgList = document.getElementsByTagName(\"img\");" +
                        "imgList[" + no + "].src = \"" + localSrc + "\"" +
                        "})()");
            }
        });
    }

    // 监听
    private class MyWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return super.shouldOverrideUrlLoading(view, url);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
//            view.getSettings().setJavaScriptEnabled(true);
            super.onPageFinished(view, url);
            numOfImgs = mNeter.loadImg(lossSrcList);
//            ImgASyncTask imgTask = new ImgASyncTask();
            // html加载完成之后，添加监听图片的点击js函数
            KLog.d("加载完成诸如js函数" );
//            addImageClickListner();
            webView.loadUrl("javascript:initImgClick()");
            if( imgState == null){
                webView.loadUrl("javascript:initImgPlaceholder()");
            }else if(!imgState.equals("")){
                Gson gson = new Gson();
                Type type = new TypeToken<ExtraImg>() {}.getType();
                extraImg = gson.fromJson(imgState, type);
                if(extraImg.getImgStatus()==ExtraImg.DOWNLOAD_ING){
                    lossSrcList = extraImg.getLossImgs();
                    if ( lossSrcList==null || lossSrcList.size()==0 ){
                        return;
                    }
                    int length = lossSrcList.size();
                    obtainSrcList = new ArrayMap<>( length );
                    StringBuilder imgNoArray = new StringBuilder("");
                    for (int i = 0; i < length; i++) {
                        imgNoArray.append( lossSrcList.keyAt(i)-1 ); // imgState 里的图片下标是从1开始的
                        imgNoArray.append( "_" );
                    }

                    imgNoArray.deleteCharAt(imgNoArray.length()-1);
                    KLog.d("传递的值" + imgNoArray);
                    webView.loadUrl("javascript:appointImgPlaceholder("+ "\"" + imgNoArray.toString() + "\"" +")");
                }
            }
        }
        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
//            view.getSettings().setJavaScriptEnabled(true);
            super.onPageStarted(view, url, favicon);
            webView.getSettings().setBlockNetworkImage(false);
        }

    }

    private void initStateView(){
        if(sReadState.equals(API.ART_UNREAD)) {
            vRead.setText(getString(R.string.font_readed));
            sReadState = API.ART_READ;
            article.setReadState(API.ART_READ);
            WithDB.getInstance().saveArticle(article);
            mNeter.postReadArticle(articleID);
            KLog.d("【 ReadState 】" + WithDB.getInstance().getArticle(articleID).getReadState());
        }else if(sReadState.equals(API.ART_READ)){
            vRead.setText(getString(R.string.font_readed));
        }else if(sReadState.equals(API.ART_READING)){
            vRead.setText(getString(R.string.font_unread));
        }
        if(sStarState.equals(API.ART_UNSTAR)){
            vStar.setText(getString(R.string.font_unstar));
        } else {
            vStar.setText(getString(R.string.font_stared));
        }
    }


    @Override
    protected void notifyDataChanged(){
        if(showContent==null || showContent.equals("")){
            KLog.d("【重载 initData 】" );
            initData();
        }else {
            webView.loadDataWithBaseURL(getBaseUrl(htmlState), showContent, "text/html", "utf-8", null);  //  contentView.reload();这种刷新方法无效
            KLog.d("【重载】");
        }
    }
    // 非静态匿名内部类的实例，所以它持有外部类Activity的引用
    // 所以此处的 handler 会持有外部类 Activity 的引用，消息队列是在一个Looper线程中不断轮询处理消息。
    // 那么当这个Activity退出时消息队列中还有未处理的消息或者正在处理消息，而消息队列中的Message持有mHandler实例的引用，mHandler又持有Activity的引用，所以导致该Activity的内存资源无法及时回收，引发内存泄漏

    protected Handler handler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            String info = msg.getData().getString("res");
            String url = msg.getData().getString("url");
            String filePath ="";
            int imgNo;
            if ( info == null ){
                info = "";
            }
            KLog.d("【handler】" +  msg.what + handler + url );
            switch (msg.what) {
                case API.S_EDIT_TAG:
                    long logTime = msg.getData().getLong("logTime");
                    if(!info.equals("OK")){
                        mNeter.forData(url,API.request,logTime);
                        KLog.d("【返回的不是 ok");
                    }
                    break;
                case API.S_ARTICLE_CONTENTS:
                    Parser.instance().parseArticleContents(info);
                    initData(); // 内容重载
                    break;
                case API.S_BITMAP:
                    imgNo = msg.getData().getInt("imgNo");
                    SrcPair imgSrcPair = lossSrcList.get(imgNo);
                    if (imgSrcPair == null) {
//                        KLog.i("【 imgSrc为空 】==");
//                        UToast.showShort("");
                        imgSrcPair = obtainSrcList.get(imgNo);
                    }else {
                        obtainSrcList.put(imgNo, lossSrcList.get(imgNo) );
                        lossSrcList.remove(imgNo);
                    }
                    KLog.i("【2】" + lossSrcList.size()  + obtainSrcList.get(imgNo)  );
                    numOfGetImgs = numOfGetImgs + 1;
                    KLog.i("【 API.S_BITMAP 】" + imgNo + "=" + numOfGetImgs + "--" + numOfImgs);
                    if( numOfGetImgs >= numOfImgs ) { // || numOfGetImgs % 5 == 0
                        KLog.i("【图片全部下载完成】" + numOfGetImgs + "=" +  numOfImgs );
                        webView.clearCache(true);
                        lossSrcList.clear();
                        logImgStatus(ExtraImg.DOWNLOAD_OVER);
                        UToast.showShort("图片全部下载完成");
//                        webView.notify();
                    }else {
                        logImgStatus(ExtraImg.DOWNLOAD_ING);
                    }
                    KLog.i("【1】" + imgSrcPair);
                    if (imgSrcPair != null) {
                        replaceSrc(imgNo, imgSrcPair.getLocalSrc());
                    }
                    break;
                case API.F_BITMAP:
                    imgNo = msg.getData().getInt("imgNo");
                    if (failImgList == null){
                        failImgList = new SparseIntArray(numOfImgs);
                    }
                    numOfFailureImg = failImgList.get(imgNo,0);
                    failImgList.put( imgNo, numOfFailureImg + 1 );
//                    numOfFailureImg = numOfFailureImg + 1;
                    KLog.i("【 API.F_BITMAP 】" + imgNo + "=" + numOfFailureImg + "--" + numOfImgs);
                    if ( numOfFailureImg > numOfFailures ){
                        UToast.showShort( "图片无法下载，请稍候再试" );
                        break;
                    }
                    filePath = msg.getData().getString("filePath");
                    mNeter.loadImg(url, filePath, imgNo);
                    break;
                case API.F_Request:
                case API.F_Response:
                    if( info.equals("Authorization Required")){
                        UToast.showShort("没有Authorization，请重新登录");
                        finish();
                        goTo(LoginActivity.TAG,"Login For Authorization");
                        break;
                    }
                    numOfFailure = numOfFailure + 1;
                    if (numOfFailure < 3){
                        mNeter.forData(url,API.request,0);
                        break;
                    }
                    UToast.showShort("网络不好，中断");
                    break;
                case API.F_NoMsg:
                    break;
            }
            return false;
        }
    });


    /**
     * 在初次进入 html 获得 imgList 时，记录值 DOWNLOAD_ING。
     * 在每次成功下载到图片时，记录 DOWNLOAD_ING。
     * 在所有下载完成时，记录 DOWNLOAD_OVER。
     * @param imgStatus 有两个值：DOWNLOAD_ING(下载中) 和 DOWNLOAD_OVER(下载完成)
     */
    private void logImgStatus(int imgStatus){
        if(extraImg==null){
            extraImg = new ExtraImg();
        }
        extraImg.setImgStatus(imgStatus);
        extraImg.setObtainImgs(obtainSrcList);
        extraImg.setLossImgs(lossSrcList);
        article.setImgState( new Gson().toJson(extraImg) );
        KLog.e("【储存的imgState】" +  new Gson().toJson(extraImg) );
        WithDB.getInstance().saveArticle(article);
    }



    private Handler mHandler = new Handler();
    @Override
    public void onClick(View v) {
        KLog.d( "【 toolbar 是否双击 】" );
        switch (v.getId()) {
            case R.id.art_toolbar_num:
            case R.id.art_toolbar:
                if (mHandler.hasMessages(API.MSG_DOUBLE_TAP)) {
                    mHandler.removeMessages(API.MSG_DOUBLE_TAP);
                    vScrolllayout.smoothScrollTo(0, 0);
                } else {
                    mHandler.sendEmptyMessageDelayed(API.MSG_DOUBLE_TAP, ViewConfiguration.getDoubleTapTimeout());
                }
                break;
        }
    }

    public void onStarClick(View view){
        String fileName = article.getTitle();
        ArrayList<String> htmlMsg = UFile.readHtml(fileNameInMD5, fileName);

        if(sStarState.equals(API.ART_UNSTAR)){
            changeStarState(API.ART_STAR);
            UToast.showShort("已收藏");

            if (htmlMsg == null) {
                return;
            }
            if (htmlMsg.get(0).equals("box")) {
                UFile.moveFile(App.boxRelativePath + fileName + ".html", App.storeRelativePath + fileName + ".html");// 移动文件
                UFile.moveDir(App.boxRelativePath + fileName + "_files", App.storeRelativePath + fileName + "_files");// 移动目录
                article.setCoverSrc(App.storeAbsolutePath + fileName + "_files" + File.separator + UString.getFileNameExtByUrl(article.getCoverSrc()));
            }
        }else {
            changeStarState(API.ART_UNSTAR);
            UToast.showShort("取消收藏");

            if (htmlMsg == null) {
                return;
            }
            if (htmlMsg.get(0).equals("store")) {
                UFile.moveFile(App.storeRelativePath + fileName + ".html", App.boxRelativePath + fileName + ".html");// 移动文件
                UFile.moveDir(App.storeRelativePath + fileName + "_files", App.boxRelativePath + fileName + "_files");// 移动目录
                article.setCoverSrc(App.boxAbsolutePath + fileName + "_files" + File.separator + UString.getFileNameExtByUrl(article.getCoverSrc()));
            }
        }
    }
    public void onLabelClick(View view){

        final List<Tag> tagsList = WithDB.getInstance().loadTags();
        ArrayList<String> tags = new ArrayList<>(tagsList.size()) ;
        for( Tag tag: tagsList ) {
            tags.add(tag.getTitle());
        }

        new MaterialDialog.Builder(this)
                .title(R.string.article_choose_tag_dialog_title)
                .items( tags )
                .itemsCallbackSingleChoice( -1, new MaterialDialog.ListCallbackSingleChoice() {
                    @Override
                    public boolean onSelection(MaterialDialog dialog, View view, int which, CharSequence text) {
                        String tagId = tagsList.get(which).getId();
//                        String articleCategories = m.replaceAll( tagId );
//                        String articleCate = m.replaceFirst( tagId );
//                        KLog.d("【被选择文章的分类2】" + articleCategories + articleCate );
//                        String categories = article.getCategories();
//                        KLog.d("【被选择文章的分类1】" + categories );
                        StringBuilder newCategories = new StringBuilder(  article.getCategories().length()  );
                        String[] cateArray = article.getCategories().replace("]","").replace("[","").split(", ");
                        for (String cate:cateArray){
                            if (cate.contains("user/" + App.mUserID + "/label/")) {
                                mNeter.postRemoveArticleTags( articleID,cate);
                                KLog.d("【-】" + cate );
                            }else {
                                newCategories.append(cate);
                                newCategories.append(", ");
                            }
                        }
                        newCategories.append(tagId);
                        newCategories.append("]");
                        KLog.d("【==】" + newCategories + articleID);
                        article.setCategories( newCategories.toString() );
                        mNeter.postAddArticleTags(articleID,tagId);
                        mNeter.postStarArticle( articleID );
                        dialog.dismiss();
                        return true; // allow selection
                    }
                })
                .show();
//        WithDB.getInstance().saveArticle( article );
    }


    public void onSaveClick(View view){
        String fileName = article.getTitle();
        ArrayList<String> htmlMsg = UFile.readHtml(fileNameInMD5 ,fileName);
//        KLog.e("【getExternalFilesDir】" + getExternalFilesDir(null) );
        if (htmlMsg == null) {
            return;
        }
        String content = "", htmlState = "", filePath = "";
        htmlState = htmlMsg.get(0);
        content = htmlMsg.get(1);
        if (htmlState.equals("cache")){
            if (article.getStarState().equals(API.ART_STAR)) {
//                filePath = App.storeRelativePath + fileNameInMD5;
                saveToStore(fileNameInMD5, fileName, content);
            } else {
//                filePath = App.cacheRelativePath + fileNameInMD5;
                saveTobox(fileNameInMD5, fileName, content);
            }

//        }else if(htmlState.equals("star")){
//            if( article.getStarState().equals(API.ART_STAR)){
//
//            }
//            filePath = App.cacheRelativePath + fileNameInMD5 + File.separator + fileNameInMD5;
//            moveTobox(filePath, fileName, content);
        }else if(htmlState.equals("box")){
            UToast.showShort("文件已存在");
        } else if (htmlState.equals("store")) {
//            UFile.moveFile( App.storeRelativePath + fileName + ".html", App.boxRelativePath  + fileName + ".html" );// 移动文件
//            UFile.moveDir( App.storeRelativePath + fileName + "_files" , App.boxRelativePath + fileName + "_files" );// 移动目录
            UToast.showShort("文件已存在于store目录");
        }
    }


    private void saveTobox(String fileNameInMD5, String fileName, String content) {
        String filePath = App.cacheRelativePath + fileNameInMD5;
        String boxHtml = UString.reviseHtmlForBox( content ,fileName ) ;
        String fileContent = String.format( getResources().getString(R.string.box_html_format), "UTF-8",fileName, article.getCanonical() ,article.getAuthor(), UTime.getFormatDate( article.getCrawlTimeMsec() ),  boxHtml );
//        UFile.saveBoxHtml(  fileName, fileContent  );

        // 保存修正后的 html
        UFile.saveHtml(App.boxRelativePath + fileName + ".html", fileContent);
//        String targetFilePath = App.boxRelativePath  + fileName + ".html";
//        UFile.moveFile( sourceFilePath, targetFilePath );

        // 删除之前的 html
        new File(filePath + ".html").delete();
//        String sourceFilePath = filePath + ".html";
//        File cacheHtmlfile = new File(sourceFilePath);
//        cacheHtmlfile.delete();


        String soureDir = filePath + "_files";
        String targetDir = App.boxRelativePath + fileName + "_files";
        UFile.moveDir( soureDir , targetDir );// 移动文件

        KLog.e( "目录" + htmlState + filePath );
        UToast.showShort("文件导出成功");
        article.setCoverSrc(  App.boxAbsolutePath + fileName + "_files" + File.separator + UString.getFileNameExtByUrl(article.getCoverSrc()) );
        WithDB.getInstance().saveArticle(article);
    }

    private void saveToStore(String fileNameInMD5, String fileName, String content) {
        String filePath = App.cacheRelativePath + fileNameInMD5;
        String storeHtml = UString.reviseHtmlForBox(content, fileName);
        String fileContent = String.format(getResources().getString(R.string.box_html_format), "UTF-8", fileName, article.getCanonical(), article.getAuthor(), UTime.getFormatDate(article.getCrawlTimeMsec()), storeHtml);
//        UFile.saveBoxHtml(  fileName, fileContent  );

        // 保存修正后的 html
        UFile.saveHtml(App.storeRelativePath + fileName + ".html", fileContent);
//        String targetFilePath = App.boxRelativePath  + fileName + ".html";
//        UFile.moveFile( sourceFilePath, targetFilePath );

        // 删除之前的 html
        new File(filePath + ".html").delete();
//        String sourceFilePath = filePath + ".html";
//        File cacheHtmlfile = new File(sourceFilePath);
//        cacheHtmlfile.delete();


        String soureDir = filePath + "_files";
        String targetDir = App.storeRelativePath + fileName + "_files";
        UFile.moveDir(soureDir, targetDir);// 移动文件

        KLog.e("目录" + htmlState + filePath);
        UToast.showShort("文件导出成功");
        article.setCoverSrc(App.storeAbsolutePath + fileName + "_files" + File.separator + UString.getFileNameExtByUrl(article.getCoverSrc()));
        WithDB.getInstance().saveArticle(article);
    }

    public void onReadClick(View view){
        if(sReadState.equals(API.ART_READ)){
            changeReadIcon(API.ART_UNREAD);
            UToast.showShort("未读");
        }else {
            changeReadIcon(API.ART_READ);
            UToast.showShort("已读");
        }
    }
    private void changeReadIcon(String iconState){
        sReadState = iconState; // 在使用过程中，只会 涉及 read 与 reading 的转换。unread 仅作为用户未主动修改文章状态是的默认状态，reading 不参与勾选为已读
        if(iconState.equals(API.ART_READ)){
//            vRead.setImageDrawable(getDrawable(R.drawable.ic_vector_all));
            vRead.setText(getString(R.string.font_readed));

            article.setReadState(API.ART_READ);
            WithDB.getInstance().saveArticle(article);

            mNeter.postReadArticle(articleID);
            KLog.d("【 标为已读 】");
        }else {
//            vRead.setImageDrawable(getDrawable(R.drawable.ic_vector_unread));
            vRead.setText(getString(R.string.font_unread));
            article.setReadState(API.ART_READING);
            WithDB.getInstance().saveArticle(article);
            mNeter.postUnReadArticle(articleID);
            KLog.d("【 标为未读 】");
        }
    }
    private void changeStarState(String iconState){
        sStarState = iconState;
        if(iconState.equals(API.ART_STAR)){
//            vStar.setImageDrawable(getDrawable(R.drawable.ic_vector_star));
            vStar.setText(getString(R.string.font_stared));
            article.setStarState(API.ART_STAR);
            WithDB.getInstance().saveArticle(article);
            mNeter.postStarArticle(articleID);
        }else {
//            vStar.setImageDrawable(getDrawable(R.drawable.ic_vector_unstar));
            vStar.setText(getString(R.string.font_unstar));
            article.setStarState(API.ART_UNSTAR);
            WithDB.getInstance().saveArticle(article);
            mNeter.postUnStarArticle(articleID);
        }
    }



}
