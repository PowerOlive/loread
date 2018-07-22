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
package me.wizos.loread.contentextractor;

import android.text.TextUtils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.socks.library.KLog;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;
import org.jsoup.select.NodeVisitor;

import java.io.File;
import java.lang.reflect.Type;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import me.wizos.loread.App;
import me.wizos.loread.utils.FileUtil;

/**
 * Extractor could extract content,title,time from news webpage
 *
 * @author hu
 */
public class Extractor {
    protected Document doc;

    Extractor(Document doc) {
        this.doc = doc;
    }

    protected HashMap<Element, CountInfo> infoMap = new HashMap<Element, CountInfo>();

    class CountInfo {

        int textCount = 0;
        int linkTextCount = 0;
        int tagCount = 0;
        int linkTagCount = 0;
        double density = 0;
        double densitySum = 0;
        double score = 0;
        int pCount = 0;
        ArrayList<Integer> leafList = new ArrayList<Integer>();

    }

    // ,iframe ,br
    private void clean() {
        doc.select("script,noscript,style").remove();
    }

    private CountInfo computeInfo(Node node) {

        if (node instanceof Element) {
            Element tag = (Element) node;

            CountInfo countInfo = new CountInfo();
            for (Node childNode : tag.childNodes()) {
                CountInfo childCountInfo = computeInfo(childNode);
                countInfo.textCount += childCountInfo.textCount;
                countInfo.linkTextCount += childCountInfo.linkTextCount;
                countInfo.tagCount += childCountInfo.tagCount;
                countInfo.linkTagCount += childCountInfo.linkTagCount;
                countInfo.leafList.addAll(childCountInfo.leafList);
                countInfo.densitySum += childCountInfo.density;
                countInfo.pCount += childCountInfo.pCount;
            }
            countInfo.tagCount++;
            String tagName = tag.tagName();
            if (tagName.equals("a")) {
                countInfo.linkTextCount = countInfo.textCount;
                countInfo.linkTagCount++;
            } else if (tagName.equals("p")) {
                countInfo.pCount++;
            }

            int pureLen = countInfo.textCount - countInfo.linkTextCount;
            int len = countInfo.tagCount - countInfo.linkTagCount;
            if (pureLen == 0 || len == 0) {
                countInfo.density = 0;
            } else {
                countInfo.density = (pureLen + 0.0) / len;
            }

            infoMap.put(tag, countInfo);

            return countInfo;
        } else if (node instanceof TextNode) {
            TextNode tn = (TextNode) node;
            CountInfo countInfo = new CountInfo();
            String text = tn.text();
            int len = text.length();
            countInfo.textCount = len;
            countInfo.leafList.add(len);
            return countInfo;
        } else {
            return new CountInfo();
        }
    }

    protected double computeScore(Element tag) {
        CountInfo countInfo = infoMap.get(tag);
        double var = Math.sqrt(computeVar(countInfo.leafList) + 1);
        double score = Math.log(var) * countInfo.densitySum * Math.log(countInfo.textCount - countInfo.linkTextCount + 1) * Math.log10(countInfo.pCount + 2);
        return score;
    }

    protected double computeVar(ArrayList<Integer> data) {
        if (data.size() == 0) {
            return 0;
        }
        if (data.size() == 1) {
            return data.get(0) / 2;
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
        clean();
        computeInfo(doc.body());
        double maxScore = 0;
        Element content = null;
        for (Map.Entry<Element, CountInfo> entry : infoMap.entrySet()) {
            Element tag = entry.getKey();
            if (tag.tagName().equals("a") || tag == doc.body()) {
                continue;
            }
            double score = computeScore(tag);
            if (score > maxScore) {
                maxScore = score;
                content = tag;
            }
        }

        if (content != null) {
//            KLog.e("正文是：" + content.text());
            return content;
        }
        KLog.e("提取失败");
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

    protected String getTime(Element contentElement) throws Exception {
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

    protected String getDate(Element contentElement) throws Exception {
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

    protected double strSim(String a, String b) {
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

    protected String getTitle(final Element contentElement) throws Exception {
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

    protected String getTitleByEditDistance(Element contentElement) throws Exception {
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

    protected int lcs(String x, String y) {

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

    protected int editDistance(String word1, String word2) {
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


//    /*输入Jsoup的Document，获取正文所在Element*/
//    public static Element getContentElementByDoc(Document doc) throws Exception {
//        Extractor ce = new Extractor(doc);
//        return ce.getContentElement();
//    }

//    /*输入HTML，获取正文所在Element*/
//    public static Element getContentElementByHtml(String html) throws Exception {
//        Document doc = Jsoup.parse(html);
//        return getContentElementByDoc(doc);
//    }
//
//    /*输入HTML和URL，获取正文所在Element*/
//    public static Element getContentElementByHtml(String html, String url) throws Exception {
//        Document doc = Jsoup.parse(html, url);
//        return getContentElementByDoc(doc);
//    }

//    /*输入URL，获取正文所在Element*/
//    public static Element getContentElementByUrl(String url) throws Exception {
////        HttpRequest request = new HttpRequest(url);
////        String html = request.response().decode();
//        return getContentElementByHtml(OkGo.get(url).execute().body().string(), url);
//    }

    /*输入Jsoup的Document，获取正文文本*/
    public static String getContentByDoc(String url, Document doc) { // throws Exception
        Extractor ce = new Extractor(doc);
        Element newDoc = ce.getContentElement();
        if (newDoc == null) {
            return "";
        }
        KLog.e("自动获取规则：" + newDoc.cssSelector());
        saveSiteRule(url, newDoc.cssSelector());
        return newDoc.html();
    }

    public static void saveSiteRule(String url, String cssSelector) {
        Map<String, List<Object>> ruleDict = new HashMap<>();
        ArrayList<Object> body = new ArrayList<>();
        body.add(cssSelector);
        ruleDict.put("body", body);
        Gson gson = new GsonBuilder()
                .setPrettyPrinting() //对结果进行格式化，增加换行
                .disableHtmlEscaping() //避免Gson使用时将一些字符自动转换为Unicode转义字符
                .create();
        try {
            URL uri = new URL(url);
            File file = new File(App.externalFilesDir + "/config/sites_generate/" + uri.getHost() + ".json");
            if (file.exists()) {
                return;
            }
            FileUtil.save(App.externalFilesDir + "/config/sites_generate/" + uri.getHost() + ".json", gson.toJson(ruleDict, HashMap.class));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String getContentByRule(Document document, Map<String, List<Object>> ruleDict) { // throws Exception
        Elements elements = document.getAllElements();
        Elements temp = new Elements();
        List<Object> commandValues;
        if (ruleDict.containsKey("body")) {
            commandValues = ruleDict.get("body");
            for (Object commandValue : commandValues) {
                temp = elements.select(commandValue.toString());
                KLog.e("Body规则：" + commandValue);
                if (!temp.isEmpty()) {
                    elements = temp;
                    break;
                }
            }
        }

//        KLog.e("此时内容为A：" + elements.html());
        if (ruleDict.containsKey("strip")) {
            commandValues = ruleDict.get("strip");
            for (Object commandValue : commandValues) {
                elements.select(commandValue.toString()).remove();
                KLog.e("Strip规则：" + commandValue);
            }
        }
//        KLog.e("此时内容为B：" + elements.html());
        return elements.html();
    }

    /*输入Jsoup的Document，获取正文文本*/
    public static String getContent(String url, Document doc) { // throws Exception
        try {
            URL uri = new URL(url);
            String siteConfigContent = FileUtil.readFile("" + App.externalFilesDir + "/config/sites/" + uri.getHost() + ".json");

            KLog.e("获取到的内容：" + siteConfigContent);
            if (!TextUtils.isEmpty(siteConfigContent)) {
                Type type = new TypeToken<Map<String, List<Object>>>() {
                }.getType();
                Map<String, List<Object>> ruleDict = new Gson().fromJson(new String(siteConfigContent), type);
                return getContentByRule(doc, ruleDict);
            } else {
                return getContentByDoc(url, doc);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
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
//    public static String getContentHtmlByUrl(String url) throws Exception {
//        Document doc = Jsoup.connect(url).get();
//        return getContentElementByDoc(doc).outerHtml();
//    }

//    /*输入HTML和URL，获取正文文本*/
//    public static String getContentByHtml(String html, String url) throws Exception {
//        Document doc = Jsoup.parse(html, url);
//        return getContentElementByDoc(doc).text();
//    }

//    /*输入URL，获取正文文本*/
//    public static String getContentByUrl(String url) throws Exception {
//        HttpRequest request = new HttpRequest(url);
//        String html = request.response().decode();
//        return getContentByHtml(html, url);
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
//    public static ModPage getNewsByHtml(String html, String url) throws Exception {
//        Document doc = Jsoup.parse(html, url);
//        return getNewsByDoc(doc);
//    }

//    /*输入URL，获取结构化新闻信息*/
//    public static ModPage getNewsByUrl(String url) throws Exception {
////        HttpRequest request = new HttpRequest(url);
////        String html = request.response().decode();
//        return getNewsByHtml(OkGo.get(url).execute().body().string(), url);
//    }

//    public static void main(String[] args) throws Exception {
//        ModPage news = Extractor.getNewsByUrl("http://www.huxiu.com/article/121959/1.html");
//        System.out.println(news.getUrl());
//        System.out.println(news.getTitle());
//        System.out.println(news.getTime());
//        System.out.println(news.getContent());
//        //System.out.println(news.getContentElement());
//        //System.out.println(news);
//    }

}
