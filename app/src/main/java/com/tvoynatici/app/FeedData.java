package com.tvoynatici.app;

import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.StringReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Haber (RSS) ve YouTube (Atom) akislarini okur. */
public class FeedData {

    public static class News {
        public String title = "";
        public String link = "";
        public String image = "";
        public String category = "";
        public long time = 0;
    }

    public static class Video {
        public String id = "";
        public String title = "";
        public String link = "";
        public String thumb = "";
        public long time = 0;
        public long views = -1;
    }

    private static String clean(String x) {
        if (x == null) return "";
        x = x.replace("﻿", "").trim();
        return x;
    }

    public static List<News> parseNews(String xml) {
        List<News> out = new ArrayList<>();
        if (xml == null || xml.trim().isEmpty()) return out;
        try {
            XmlPullParser p = Xml.newPullParser();
            p.setInput(new StringReader(clean(xml)));
            News cur = null;
            int ev = p.getEventType();
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG) {
                    String n = p.getName();
                    if ("item".equals(n)) {
                        cur = new News();
                    } else if (cur != null) {
                        if ("title".equals(n)) {
                            cur.title = clean(p.nextText());
                        } else if ("link".equals(n)) {
                            cur.link = clean(p.nextText());
                        } else if ("pubDate".equals(n)) {
                            cur.time = parseRssDate(clean(p.nextText()));
                        } else if ("category".equals(n)) {
                            if (cur.category.isEmpty()) cur.category = clean(p.nextText());
                        } else if ("enclosure".equals(n)) {
                            String u = p.getAttributeValue(null, "url");
                            if (u != null && cur.image.isEmpty()) cur.image = u.trim();
                        } else if ("media:content".equals(n) || "media:thumbnail".equals(n)) {
                            String u = p.getAttributeValue(null, "url");
                            if (u != null && cur.image.isEmpty()) cur.image = u.trim();
                        } else if ("description".equals(n)) {
                            String d = p.nextText();
                            if (cur.image.isEmpty() && d != null) {
                                int i = d.indexOf("src=\"");
                                if (i >= 0) {
                                    int j = d.indexOf('"', i + 5);
                                    if (j > i) cur.image = d.substring(i + 5, j);
                                }
                            }
                        }
                    }
                } else if (ev == XmlPullParser.END_TAG && "item".equals(p.getName()) && cur != null) {
                    if (!cur.title.isEmpty() && cur.link.startsWith("http")) out.add(cur);
                    cur = null;
                    if (out.size() >= 15) break;
                }
                ev = p.next();
            }
        } catch (Exception ignored) {
            // Bozuk ya da yarim kalmis akista elde olanlar gosterilir
        }
        return out;
    }

    public static List<Video> parseVideos(String xml) {
        List<Video> out = new ArrayList<>();
        if (xml == null || xml.trim().isEmpty()) return out;
        try {
            XmlPullParser p = Xml.newPullParser();
            p.setInput(new StringReader(clean(xml)));
            Video cur = null;
            int ev = p.getEventType();
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG) {
                    String n = p.getName();
                    if ("entry".equals(n)) {
                        cur = new Video();
                    } else if (cur != null) {
                        if ("yt:videoId".equals(n)) {
                            cur.id = clean(p.nextText());
                        } else if ("title".equals(n)) {
                            cur.title = clean(p.nextText());
                        } else if ("published".equals(n)) {
                            cur.time = parseIsoDate(clean(p.nextText()));
                        } else if ("media:statistics".equals(n)) {
                            String v = p.getAttributeValue(null, "views");
                            if (v != null) {
                                try {
                                    cur.views = Long.parseLong(v.trim());
                                } catch (NumberFormatException ignored) {
                                    cur.views = -1;
                                }
                            }
                        }
                    }
                } else if (ev == XmlPullParser.END_TAG && "entry".equals(p.getName()) && cur != null) {
                    if (!cur.id.isEmpty() && !cur.title.isEmpty()) {
                        cur.link = "https://www.youtube.com/watch?v=" + cur.id;
                        cur.thumb = "https://i.ytimg.com/vi/" + cur.id + "/mqdefault.jpg";
                        out.add(cur);
                    }
                    cur = null;
                    if (out.size() >= 12) break;
                }
                ev = p.next();
            }
        } catch (Exception ignored) {
            // Bozuk ya da yarim kalmis akista elde olanlar gosterilir
        }
        return out;
    }

    // Son care: akis alinamazsa kanalin "Videolar" sayfasindan kimlik ve baslik okunur
    public static List<Video> parseVideosFromHtml(String html) {
        List<Video> out = new ArrayList<>();
        if (html == null || html.isEmpty()) return out;
        try {
            Set<String> seen = new HashSet<>();
            Matcher m = Pattern.compile("\"videoId\":\"([A-Za-z0-9_-]{11})\"").matcher(html);
            Pattern titlePat = Pattern.compile(
                    "\"title\":\\{\"runs\":\\[\\{\"text\":\"((?:[^\"\\\\]|\\\\.)*)\"");
            while (m.find() && out.size() < 12) {
                String id = m.group(1);
                if (!seen.add(id)) continue;
                int from = m.end();
                int end = Math.min(html.length(), from + 3000);
                Matcher t = titlePat.matcher(html.substring(from, end));
                String title = t.find() ? unescape(t.group(1)) : "";
                if (title.isEmpty()) continue;
                Video v = new Video();
                v.id = id;
                v.title = title;
                v.link = "https://www.youtube.com/watch?v=" + id;
                v.thumb = "https://i.ytimg.com/vi/" + id + "/mqdefault.jpg";
                out.add(v);
            }
        } catch (Throwable ignored) {
            // Sayfa yapisi degismisse bos doner
        }
        return out;
    }

    private static String unescape(String s) {
        return s.replace("\\u0026", "&").replace("\\\"", "\"").replace("\\/", "/")
                .replace("\\\\", "\\").replace("\\n", " ").trim();
    }

    private static long parseRssDate(String s) {
        try {
            Date d = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US).parse(s);
            return d == null ? 0 : d.getTime();
        } catch (Exception e) {
            return 0;
        }
    }

    private static long parseIsoDate(String s) {
        try {
            s = s.replaceAll("Z$", "+0000").replaceAll("([+-]\\d\\d):(\\d\\d)$", "$1$2");
            Date d = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).parse(s);
            return d == null ? 0 : d.getTime();
        } catch (Exception e) {
            return 0;
        }
    }

    public static String timeAgo(long ms) {
        if (ms <= 0) return "";
        long diff = System.currentTimeMillis() - ms;
        if (diff < 0) diff = 0;
        long min = diff / 60000;
        if (min < 2) return "az önce";
        if (min < 60) return min + " dk önce";
        long hr = min / 60;
        if (hr < 24) return hr + " sa önce";
        long day = hr / 24;
        if (day == 1) return "dün";
        if (day < 7) return day + " gün önce";
        if (day < 30) return (day / 7) + " hafta önce";
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(ms);
        return String.format(Locale.US, "%02d.%02d.%d",
                c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.MONTH) + 1, c.get(Calendar.YEAR));
    }

    public static String viewsText(long v) {
        if (v < 0) return "";
        if (v < 1000) return v + " izlenme";
        if (v < 1000000) return String.format(Locale.US, "%.1f", v / 1000.0).replace(".0", "")
                .replace('.', ',') + " B izlenme";
        return String.format(Locale.US, "%.1f", v / 1000000.0).replace(".0", "")
                .replace('.', ',') + " Mn izlenme";
    }
}
