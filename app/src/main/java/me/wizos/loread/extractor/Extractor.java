/*
 * Copyright (C) 2015 hu
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package me.wizos.loread.extractor;

import com.orhanobut.logger.Logger;
import com.socks.library.KLog;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;
import org.jsoup.select.NodeVisitor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extractor could extract content,title,time from news webpage
 *
 * 本工具在是在 WebCollector 的基础上参考 GeneralNewsExtractor 项目，增加“去除干扰元素”的功能，以增加提取精准度
 * @author hu，https://github.com/CrawlScript/WebCollector
 */
public class Extractor {
    private Document doc;
    public Extractor(Document doc) {
        this.doc = doc;
    }

    private HashMap<Element, CountInfo> bodyInfoMap = new HashMap<Element, CountInfo>();

    static class CountInfo {
        int textCount = 0;
        int linkTextCount = 0;
        int tagCount = 0;
        int linkTagCount = 0;
        double density = 0;
        double densitySum = 0;
        //double score = 0;
        int pCount = 0;
        int punctuationCount = 0;
        //double sbdi = 0;
        ArrayList<Integer> leafList = new ArrayList<Integer>();
    }

    // ,iframe ,br
    private void cleanBodyElement(Element body) {
        body.select("script, noscript, style, link").remove();
        //body.select("*.share, *.contribution, *.copyright, *.copy-right, *.disclaimer, *.recommend, *.related, *.footer, *.comment, *.social, *.submeta").remove();
        body.select("[id*=share], [id*=contribution], [id*=copyright], [id*=copy-right], [id*=-nav], [id*=-tags], [id*=disclaimer], [id*=recommend], [id*=related], [id*=relates], [id*=archive], [id*=recent_comments], [id*=footer], [id*=social], [id*=submeta], [id*=entry-meta]").remove();
        body.select("[class*=share], [class*=contribution], [class*=copyright], [class*=copy-right], [class*=-nav], [class*=-tags], [class*=disclaimer], [class*=recommend], [class*=related], [class*=relates], [class*=archive], [class*=recent_comments], [class*=footer], [class*=social], [class*=submeta], [class*=entry-meta]").remove();

//        // 以下为新增，主要是去除文章中的干扰元素
//        body.select("p:empty, div:empty, blockquote:empty, details:empty, details:empty, figure:empty, figcaption:empty").remove();
//        body.select("ul:empty, ol:empty, li:empty").remove();
//        body.select("table:empty, tbody:empty, th:empty, tr:empty, dt:empty, dl:empty").remove();
//        // 受 readability 启发，移除没有子元素（包括文本）的元素。
//        body.select("section:empty, h1:empty, h2:empty, h3:empty, h4:empty, h5:empty, h6:empty, ins:empty, a:empty, b:empty, string:empty, span:empty, i:empty").remove();

        Elements elements;
        boolean circulate;
        do {
            elements = body.select("p:empty, div:empty, blockquote:empty, details:empty, details:empty, figure:empty, figcaption:empty,  ul:empty, ol:empty, li:empty,  table:empty, tbody:empty, th:empty, tr:empty, dt:empty, dl:empty,  section:empty, h1:empty, h2:empty, h3:empty, h4:empty, h5:empty, h6:empty, ins:empty, a:empty, b:empty, string:empty, span:empty, i:empty,  section:empty, h1:empty, h2:empty, h3:empty, h4:empty, h5:empty, h6:empty, ins:empty, a:empty, b:empty, string:empty, span:empty, i:empty");
            if( elements != null && elements.size() > 0){
                //System.out.println("继续移除：" + elements.outerHtml());
                elements.remove();
                circulate = true;
            }else {
                circulate = false;
            }
        }while (circulate);
    }


    private CountInfo computeInfo(Node node) {
        if (node instanceof Element) {
            Element tag = (Element) node;
            CountInfo countInfo = new CountInfo();
            for (Node childNode : tag.childNodes()) {
                CountInfo childCountInfo = computeInfo(childNode);
                countInfo.textCount += childCountInfo.textCount; // 子节点的字符串字数
                countInfo.punctuationCount += childCountInfo.punctuationCount; // 子节点的标点符号的数量
                countInfo.linkTextCount += childCountInfo.linkTextCount;
                countInfo.tagCount += childCountInfo.tagCount;
                countInfo.linkTagCount += childCountInfo.linkTagCount;
                countInfo.leafList.addAll(childCountInfo.leafList);
                countInfo.densitySum += childCountInfo.density;
                countInfo.pCount += childCountInfo.pCount;
            }

            String tagName = tag.tagName();
            if (tagName.equals("a")) {
                countInfo.linkTextCount = countInfo.textCount;
                countInfo.linkTagCount++;
            } else if (tagName.equals("p") || tagName.equals("article")) { // || tagName.equals("img") || tagName.equals("video") || tagName.equals("audio")
                countInfo.pCount++;
            }
            // 一些有样式意义的空元素不要计入打分规则，不然会有很严重的干扰，比如91论坛的文章
            else if(tagName.equals("br") || tagName.equals("hr")){ //  || tagName.equals("strong") || tagName.equals("span")
                return countInfo;
            }

            countInfo.tagCount++;

            int pureLen = countInfo.textCount - countInfo.linkTextCount;
            int len = countInfo.tagCount - countInfo.linkTagCount;
            if (pureLen == 0 || len == 0) {
                countInfo.density = 0;
            } else {
                countInfo.density = (pureLen + 0.0) / len;
            }


//            increase_tag_weight(tag, countInfo);
//            countInfo.sbdi = calcSbdi(countInfo);
//            countInfo.sbdi = (countInfo.textCount - countInfo.linkTextCount + 0.0)/(countInfo.punctuationCount + 1);
//            // sbdi 不能为0，否则会导致求对数时报错。
//            if(countInfo.sbdi == 0){
//                countInfo.sbdi = 1;
//            }

//            if( tag.className().equals("postmessage_5451354")){
//                System.out.println("节点: "  + tag.nodeName() + "." + tag.className()
//                                + ", 文本长度：" + countInfo.textCount + ", 链接文本长度：" + countInfo.linkTextCount + ", tag数量" + countInfo.tagCount + ", linkTag：" + countInfo.linkTagCount
//                                + ", p标签：" + countInfo.pCount+ " , 文字密度：" + countInfo.density + ",文本密度总数：" + countInfo.densitySum + ", 符号密度："
//                          );
//            }

            bodyInfoMap.put(tag, countInfo);

            return countInfo;
        } else if (node instanceof TextNode) {
            TextNode tn = (TextNode) node;
            CountInfo countInfo = new CountInfo();
            String text = tn.text();
            int len = text.length();
            countInfo.textCount = len;
            //countInfo.punctuationCount = countPunctuationNum(text);
            countInfo.leafList.add(len);
            return countInfo;
        } else {
            return new CountInfo();
        }
    }

    private double computeScore(Element tag) {
        CountInfo countInfo = bodyInfoMap.get(tag);
        double var = Math.sqrt(computeVar(countInfo.leafList) + 1); // 这一传是干嘛用的？
        //  * Math.log(var) * Math.log(countInfo.textCount - countInfo.linkTextCount + 1)
        return countInfo.densitySum * Math.log10(countInfo.pCount + 2) * Math.log(var) * Math.log(countInfo.textCount - countInfo.linkTextCount + 1);// 最后2个中有一个是论文中计算节点文本的标准差？根据另一个库的建议，不再需要计算文本密度的标准差
//        return countInfo.densitySum * Math.log10(countInfo.pCount + 2) * Math.log(countInfo.sbdi);
    }

    /**
     * 计算某个节点的符号密度
     */
    private double calcSbdi(CountInfo countInfo){
        double sbdi = (countInfo.textCount - countInfo.linkTextCount + 0.0)/(countInfo.punctuationCount + 1);
        // sbdi 不能为0，否则会导致求对数时报错。
        if(sbdi == 0){
            return 1;
        }else {
            return sbdi;
        }
    }
    private int countPunctuationNum(String text){
        String regEx="[^`~!@#$%^&*()—\\-+=|{}':;,\\[\\].<>/?！￥…（）《》｛｝【】‘；：”“’。， 、？]";
        Pattern p = Pattern.compile(regEx);
        Matcher m = p.matcher(text);//这里把想要替换的字符串传进来
        return m.replaceAll("").trim().length();
    }
    private void increase_tag_weight(Element element, CountInfo countInfo){
        if(element.hasClass("content") || element.hasClass("article") || element.hasClass("post") || element.hasClass("news")){
            countInfo.textCount = countInfo.textCount *2;
        }
    }

    // 用这种方式而不是符号密度，貌似速度更快
    private double computeVar(ArrayList<Integer> data) {
        if (data.size() == 0) {
            return 0;
        }
        if (data.size() == 1) {
            return (double)data.get(0) / 2;
        }
        double sum = 0;
        for (Integer i : data) {
            sum += i;
        }
        double ave = sum / data.size();
        sum = 0;
        for (Integer i : data) {
            sum += (i - ave) * (i - ave);
        }
        sum = sum / data.size();
        return sum;
    }

    /**
     * 我自己改的，去掉了报错
     *
     * @return
     */
    public Element getContentElement() {
        Element bodyElement = doc.body();
        cleanBodyElement(bodyElement);
        computeInfo(bodyElement);
        double maxScore = 0;
        Element content = null;
//        String tt = "";
//        flag = flag.trim();
        for (Map.Entry<Element, CountInfo> entry : bodyInfoMap.entrySet()) {
            Element tag = entry.getKey();
            if (tag.tagName().equals("a") || tag == bodyElement) {
                continue;
            }
            double score = computeScore(tag);

//            tt = tag.text().trim();
//            if( tt.length() > 18){
//                tt = tt.substring(0,18);
//            }
//            if(tt.startsWith(flag)){
//                score = score * 1.5;
//            }
//            System.out.println("Tag: "  + tag.nodeName() + "." + tag.className()  + " , 分数：" + score + " , 文本：" + tt);
            if (score > maxScore) {
                maxScore = score;
                content = tag;
            }
        }

        if (content != null) {
//            KLog.e("正文是：" + content.text());
            return content;
        }
        Logger.e("提取失败");
        return null;
    }

    public ModPage getNews() throws Exception {
        ModPage modPage = new ModPage();
        Element contentElement;
        try {
            contentElement = getContentElement();
            modPage.setContentElement(contentElement);
        } catch (Exception ex) {
            KLog.e("modPage content extraction failed,extraction abort", ex);
            throw new Exception(ex);
        }

        if (doc.baseUri() != null) {
            modPage.setUrl(doc.baseUri());
        }

        try {
            modPage.setTime(getTime(contentElement));
        } catch (Exception ex) {
            KLog.e("modPage title extraction failed", ex);
        }

        try {
            modPage.setTitle(getTitle(contentElement));
        } catch (Exception ex) {
            KLog.e("title extraction failed", ex);
        }
        return modPage;
    }

    private String getTime(Element contentElement) throws Exception {
        String regex = "([1-2][0-9]{3})[^0-9]{1,5}?([0-1]?[0-9])[^0-9]{1,5}?([0-9]{1,2})[^0-9]{1,5}?([0-2]?[1-9])[^0-9]{1,5}?([0-9]{1,2})[^0-9]{1,5}?([0-9]{1,2})";
        Pattern pattern = Pattern.compile(regex);
        Element current = contentElement;
        for (int i = 0; i < 2; i++) {
            if (current != null && current != doc.body()) {
                Element parent = current.parent();
                if (parent != null) {
                    current = parent;
                }
            }
        }
        for (int i = 0; i < 6; i++) {
            if (current == null) {
                break;
            }
            String currentHtml = current.outerHtml();
            Matcher matcher = pattern.matcher(currentHtml);
            if (matcher.find()) {
                return matcher.group(1) + "-" + matcher.group(2) + "-" + matcher.group(3) + " " + matcher.group(4) + ":" + matcher.group(5) + ":" + matcher.group(6);
            }
            if (current != doc.body()) {
                current = current.parent();
            }
        }

        try {
            return getDate(contentElement);
        } catch (Exception ex) {
            throw new Exception("time not found");
        }

    }

    private String getDate(Element contentElement) throws Exception {
        String regex = "([1-2][0-9]{3})[^0-9]{1,5}?([0-1]?[0-9])[^0-9]{1,5}?([0-9]{1,2})";
        Pattern pattern = Pattern.compile(regex);
        Element current = contentElement;
        for (int i = 0; i < 2; i++) {
            if (current != null && current != doc.body()) {
                Element parent = current.parent();
                if (parent != null) {
                    current = parent;
                }
            }
        }
        for (int i = 0; i < 6; i++) {
            if (current == null) {
                break;
            }
            String currentHtml = current.outerHtml();
            Matcher matcher = pattern.matcher(currentHtml);
            if (matcher.find()) {
                return matcher.group(1) + "-" + matcher.group(2) + "-" + matcher.group(3);
            }
            if (current != doc.body()) {
                current = current.parent();
            }
        }
        throw new Exception("date not found");
    }

    private double strSim(String a, String b) {
        int len1 = a.length();
        int len2 = b.length();
        if (len1 == 0 || len2 == 0) {
            return 0;
        }
        double ratio;
        if (len1 > len2) {
            ratio = (len1 + 0.0) / len2;
        } else {
            ratio = (len2 + 0.0) / len1;
        }
        if (ratio >= 3) {
            return 0;
        }
        return (lcs(a, b) + 0.0) / Math.max(len1, len2);
    }

    private String getTitle(final Element contentElement) throws Exception {
        final ArrayList<Element> titleList = new ArrayList<Element>();
        final ArrayList<Double> titleSim = new ArrayList<Double>();
        final AtomicInteger contentIndex = new AtomicInteger();
        final String metaTitle = doc.title().trim();
        if (!metaTitle.isEmpty()) {
            doc.body().traverse(new NodeVisitor() {
                @Override
                public void head(Node node, int i) {
                    if (node instanceof Element) {
                        Element tag = (Element) node;
                        if (tag == contentElement) {
                            contentIndex.set(titleList.size());
                            return;
                        }
                        String tagName = tag.tagName();
                        if (Pattern.matches("h[1-6]", tagName)) {
                            String title = tag.text().trim();
                            double sim = strSim(title, metaTitle);
                            titleSim.add(sim);
                            titleList.add(tag);
                        }
                    }
                }

                @Override
                public void tail(Node node, int i) {
                }
            });
            int index = contentIndex.get();
            if (index > 0) {
                double maxScore = 0;
                int maxIndex = -1;
                for (int i = 0; i < index; i++) {
                    double score = (i + 1) * titleSim.get(i);
                    if (score > maxScore) {
                        maxScore = score;
                        maxIndex = i;
                    }
                }
                if (maxIndex != -1) {
                    return titleList.get(maxIndex).text();
                }
            }
        }

        Elements titles = doc.body().select("*[id^=title],*[id$=title],*[class^=title],*[class$=title]");
        if (titles.size() > 0) {
            String title = titles.first().text();
            if (title.length() > 5 && title.length() < 40) {
                return titles.first().text();
            }
        }
        try {
            return getTitleByEditDistance(contentElement);
        } catch (Exception ex) {
            throw new Exception("title not found");
        }

    }

    private String getTitleByEditDistance(Element contentElement) throws Exception {
        final String metaTitle = doc.title();

        final ArrayList<Double> max = new ArrayList<Double>();
        max.add(0.0);
        final StringBuilder sb = new StringBuilder();
        doc.body().traverse(new NodeVisitor() {
            @Override
            public void head(Node node, int i) {
                if (node instanceof TextNode) {
                    TextNode tn = (TextNode) node;
                    String text = tn.text().trim();
                    double sim = strSim(text, metaTitle);
                    if (sim > 0) {
                        if (sim > max.get(0)) {
                            max.set(0, sim);
                            sb.setLength(0);
                            sb.append(text);
                        }
                    }

                }
            }

            @Override
            public void tail(Node node, int i) {
            }
        });
        if (sb.length() > 0) {
            return sb.toString();
        }
        throw new Exception();

    }

    private int lcs(String x, String y) {
        int M = x.length();
        int N = y.length();
        if (M == 0 || N == 0) {
            return 0;
        }
        int[][] opt = new int[M + 1][N + 1];

        for (int i = M - 1; i >= 0; i--) {
            for (int j = N - 1; j >= 0; j--) {
                if (x.charAt(i) == y.charAt(j)) {
                    opt[i][j] = opt[i + 1][j + 1] + 1;
                } else {
                    opt[i][j] = Math.max(opt[i + 1][j], opt[i][j + 1]);
                }
            }
        }

        return opt[0][0];

    }

    private int editDistance(String word1, String word2) {
        int len1 = word1.length();
        int len2 = word2.length();

        int[][] dp = new int[len1 + 1][len2 + 1];

        for (int i = 0; i <= len1; i++) {
            dp[i][0] = i;
        }

        for (int j = 0; j <= len2; j++) {
            dp[0][j] = j;
        }

        for (int i = 0; i < len1; i++) {
            char c1 = word1.charAt(i);
            for (int j = 0; j < len2; j++) {
                char c2 = word2.charAt(j);

                if (c1 == c2) {
                    dp[i + 1][j + 1] = dp[i][j];
                } else {
                    int replace = dp[i][j] + 1;
                    int insert = dp[i][j + 1] + 1;
                    int delete = dp[i + 1][j] + 1;

                    int min = replace > insert ? insert : replace;
                    min = delete > min ? min : delete;
                    dp[i + 1][j + 1] = min;
                }
            }
        }

        return dp[len1][len2];
    }


//    /*输入Jsoup的Document，获取正文文本*/
//    public static String getContentByDoc(Document doc,String mKeyWord) { // throws Exception
//        Extractor ce = new Extractor(doc,mKeyWord);
//        Element newDoc = ce.getContentElement();
//
//        KLog.e("含关键字的正文3是：" + newDoc.outerHtml());
//        if( newDoc == null ){
//            return "";
//        }
//        KLog.e("含关键字的正文4是：" + newDoc.outerHtml());
//        return newDoc.outerHtml();
//    }

//    /*输入HTML，获取正文文本*/
//    public static String getContentByHtml(String html) throws Exception {
//        Document doc = Jsoup.parse(html);
//        return getContentElementByDoc(doc).text();
//    }
//    /*输入HTML，获取正文文本*/
//    public static String getContentHtml(String html) throws Exception {
//        Document doc = Jsoup.parse(html);
//        return getContentElementByDoc(doc).text();
//    }
//    public static String getContentHtml(String baseUri,InputStream inputStream) throws Exception {
//        Document doc = Jsoup.parse(inputStream,null,baseUri);
//        KLog.e("编码是2：" + doc.charset() );
//        return getContentElementByDoc(doc).outerHtml();
//    }
//    public static String getContentHtml(String baseUri,Document doc) throws Exception {
//        KLog.e("编码是2：" + doc.charset() );
//        return getContentElementByDoc(doc).outerHtml();
//    }
//    public static String getContentHtmlByUrl(String content) throws Exception {
//        Document doc = Jsoup.connect(content).get();
//        return getContentElementByDoc(doc).outerHtml();
//    }

//    /*输入HTML和URL，获取正文文本*/
//    public static String getContentByHtml(String html, String content) throws Exception {
//        Document doc = Jsoup.parse(html, content);
//        return getContentElementByDoc(doc).text();
//    }

//    /*输入URL，获取正文文本*/
//    public static String getContentByUrl(String content) throws Exception {
//        HttpRequest request = new HttpRequest(content);
//        String html = request.response().decode();
//        return getContentByHtml(html, content);
//    }

//    /*输入Jsoup的Document，获取结构化新闻信息*/
//    public static ModPage getNewsByDoc(Document doc) throws Exception {
//        Extractor ce = new Extractor(doc);
//        return ce.getNews();
//    }

//    /*输入HTML，获取结构化新闻信息*/
//    public static ModPage getNewsByHtml(String html) throws Exception {
//        Document doc = Jsoup.parse(html);
//        return getNewsByDoc(doc);
//    }

//    /*输入HTML和URL，获取结构化新闻信息*/
//    public static ModPage getNewsByHtml(String html, String content) throws Exception {
//        Document doc = Jsoup.parse(html, content);
//        return getNewsByDoc(doc);
//    }

//    /*输入URL，获取结构化新闻信息*/
//    public static ModPage getNewsByUrl(String content) throws Exception {
////        HttpRequest request = new HttpRequest(content);
////        String html = request.response().decode();
//        return getNewsByHtml(OkGo.get(content).execute().body().string(), content);
//    }

//    public static void main(String[] args) throws Exception {
//        ModPage news = Extractor.getNewsByUrl("http://www.huxiu.com/article/121959/1.html");
//        System.out.println(news.getContent());
//        System.out.println(news.getTitle());
//        System.out.println(news.getTime());
//        System.out.println(news.getData());
//        //System.out.println(news.getContentElement());
//        //System.out.println(news);
//    }

}
