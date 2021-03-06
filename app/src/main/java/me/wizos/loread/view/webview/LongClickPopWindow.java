package me.wizos.loread.view.webview;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.WebView;
import android.widget.PopupWindow;

import com.hjq.toast.ToastUtils;
import com.socks.library.KLog;

import me.wizos.loread.R;

/**
 * @author Wizos on 2018/9/16.
 */

public class LongClickPopWindow extends PopupWindow {
    private View webViewLongClickedPopWindow;
    private Context context;
    private WebView.HitTestResult result;
    private WebView webView;
    private int x, y;

    /**
     * 构造函数
     *
     * @param context 上下文
     * @param width   宽度
     * @param height  高度 *
     */
    public LongClickPopWindow(Context context, WebView webView, int width, int height, int x, int y) {
        super(context);
        if (context == null | webView == null) {
            return;
        }
        this.result = webView.getHitTestResult();
        if (null == result) {
            return;
        }
        if (result.getType() == WebView.HitTestResult.UNKNOWN_TYPE) {
            KLog.e("长按未知：" + result.getType() + " , " + result.getExtra());
            return;
        }
        this.context = context;
        this.webView = webView;
        this.x = x;
        this.y = y;
        LayoutInflater itemLongClickedPopWindowInflater = LayoutInflater.from(this.context);
        this.webViewLongClickedPopWindow = itemLongClickedPopWindowInflater.inflate(R.layout.webview_long_clicked_popwindow, null);

        //设置默认选项
        setWidth(width);
        setHeight(height);
        setContentView(this.webViewLongClickedPopWindow);
        setOutsideTouchable(true);
        setFocusable(true);

        //创建
        initTab();
//        showAtLocation(webView, Gravity.TOP|Gravity.LEFT, downX, downY + 10);
    }

    //实例化
    private void initTab() {

        switch (result.getType()) {
//            case FAVORITES_ITEM_POPUPWINDOW:
//                this.itemLongClickedPopWindowView = this.itemLongClickedPopWindowInflater.inflate(R.layout.list_item_longclicked_favorites, null);
//                break;
//            case FAVORITES_VIEW_POPUPWINDOW: //对于书签内容弹出菜单，未作处理
//                break;
//            case HISTORY_ITEM_POPUPWINDOW:
//                this.itemLongClickedPopWindowView = this.itemLongClickedPopWindowInflater.inflate(R.layout.list_item_longclicked_history, null);
//                break;
//            case HISTORY_VIEW_POPUPWINDOW: //对于历史内容弹出菜单，未作处理
//                break;

//            case WebView.HitTestResult.EDIT_TEXT_TYPE: // 选中的文字类型
//            case WebView.HitTestResult.PHONE_TYPE: // 处理拨号
//            case WebView.HitTestResult.EMAIL_TYPE: // 处理Email
//            case WebView.HitTestResult.GEO_TYPE: // 　地图类型
//            case WebView.HitTestResult.SRC_ANCHOR_TYPE: // 超链接
//            case WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE: // 带有链接的图片类型
//            case WebView.HitTestResult.IMAGE_TYPE: // 处理长按图片的菜单项
//                String url = result.getExtra();//获取图片
//                break;
//            case WebView.HitTestResult.UNKNOWN_TYPE: //未知


            case WebView.HitTestResult.SRC_ANCHOR_TYPE://超链接
                if(TextUtils.isEmpty(result.getExtra())){
                    return;
                }
                KLog.d("超链接为：" + result.getExtra() );
                this.webViewLongClickedPopWindow.findViewById(R.id.webview_copy_link)
                        .setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                LongClickPopWindow.this.dismiss();
                                //获取剪贴板管理器：
                                ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                                // 创建普通字符型ClipData
                                ClipData mClipData = ClipData.newRawUri("url", Uri.parse(result.getExtra()));
                                // 将ClipData内容放到系统剪贴板里。
                                cm.setPrimaryClip(mClipData);
                                ToastUtils.show(context.getString(R.string.copy_success));
                            }
                        });

                this.webViewLongClickedPopWindow.findViewById(R.id.webview_share_link)
                        .setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                LongClickPopWindow.this.dismiss();
                                Intent sendIntent = new Intent(Intent.ACTION_SEND);
                                sendIntent.setType("text/plain");
                                sendIntent.putExtra(Intent.EXTRA_TEXT, result.getExtra());
//                                sendIntent.setData(Uri.parse(status.getExtra()));
                                //sendIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                context.startActivity(Intent.createChooser(sendIntent, context.getString(R.string.share_to)));
                            }
                        });

                this.webViewLongClickedPopWindow.findViewById(R.id.webview_open_mode)
                        .setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                LongClickPopWindow.this.dismiss();
                                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(result.getExtra()));
                                // 每次都要选择打开方式
                                context.startActivity(Intent.createChooser(intent, context.getString(R.string.open_mode)));
                                //context.startActivity(intent);
                            }
                        });

                showAtLocation(webView, Gravity.TOP | Gravity.START, x, y);
                break;
            case WebView.HitTestResult.IMAGE_TYPE: //图片
                // TODO: 2019/5/1 重新下载 , 查看原图
                break;
            case WebView.HitTestResult.UNKNOWN_TYPE: //对于历史内容弹出菜单，未作处理
                break;
        }
    }

}
