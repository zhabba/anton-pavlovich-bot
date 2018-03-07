package org.wyvie.chehov.bot.commands.dailymenu.restaurant;

import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CookPoint implements Restaurant {

    public static final String NAME = "cookpoint";

    private static final String URL = "http://cookpoint.cz/";

    private final Pattern pattern1;
    private final Pattern pattern2;

    public CookPoint() {
        pattern1 = Pattern.compile("<h1>Daily menu</h1>(.*?(?=</table))</table>");

        pattern2 = Pattern.compile("<strong class=\"mname\">([^<]*)</strong>.*?(?=<small)" +
                "<small>(.*?(?=</small))</small></td>" +
                "<td class=\"price\">(.*?(?=</td))</td>");
    }

    @Override
    public String menu() {
        String pageSource = "";

        try {
            pageSource = URLUtils.getPageSource(URL);
        } catch (IOException ignore) {
        }

        StringBuilder stringBuilder = new StringBuilder("");

        if (!StringUtils.isEmpty(pageSource)) {
            Matcher matcher = pattern1.matcher(pageSource);

            if (matcher.find()) {
                String inner = matcher.group(1);

                matcher = pattern2.matcher(inner);
                while (matcher.find()) {
                    String name1 = matcher.group(1);
                    String name2 = matcher.group(2)
                            .replace("<strong>", "")
                            .replace("</strong>", "");
                    String price = matcher.group(3).replace("&nbsp;", "");

                    stringBuilder.append(name1).append("\n").append(name2).append("\t").append(price).append("\n\n");
                }
            }
        }

        return stringBuilder.toString().trim();
    }
}
