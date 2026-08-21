package com.lin.common.utils;

import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;
import org.apache.commons.lang3.StringUtils;

public class PinYinConvertUtils {

    /**
     * 测试main方法
     *
     * @param args
     */
    public static void main(String[] args) {
        System.out.println(toFirstChar("汉字转换为拼音").toUpperCase()); //转为首字母大写
        System.out.println(toPinyin("汉字转换为拼音"));
    }

    /**
     * 获取字符串拼音的第一个字母
     *
     * @param chinese
     * @return
     */
    public static String toFirstChar(String chinese) {
        String pinyinStr = "";
        if (StringUtils.isEmpty(chinese)) {
            return pinyinStr;
        }
        char[] newChar = chinese.toCharArray();  //转为单个字符
        HanyuPinyinOutputFormat defaultFormat = new HanyuPinyinOutputFormat();
        defaultFormat.setCaseType(HanyuPinyinCaseType.LOWERCASE);
        defaultFormat.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
        for (int i = 0; i < newChar.length; i++) {
            if (newChar[i] > 128) {
                try {
                    String[] arr = PinyinHelper.toHanyuPinyinStringArray(newChar[i], defaultFormat);
                    if (arr != null && arr.length > 0) {
                        pinyinStr += arr[0].charAt(0);
                    } else {
                        // 非汉字或无法转拼音的字符，原样保留
                        pinyinStr += newChar[i];
                    }
                } catch (BadHanyuPinyinOutputFormatCombination e) {
                    pinyinStr += newChar[i];
                }
            } else {
                pinyinStr += newChar[i];
            }
        }
        return pinyinStr;
    }

    /**
     * 汉字转为拼音
     *
     * @param chinese
     * @return
     */
    public static String toPinyin(String chinese) {
        String pinyinStr = "";
        if (StringUtils.isEmpty(chinese)) {
            return pinyinStr;
        }
        char[] newChar = chinese.toCharArray();
        HanyuPinyinOutputFormat defaultFormat = new HanyuPinyinOutputFormat();
        defaultFormat.setCaseType(HanyuPinyinCaseType.LOWERCASE);
        defaultFormat.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
        for (int i = 0; i < newChar.length; i++) {
            if (newChar[i] > 128) {
                try {
                    pinyinStr += PinyinHelper.toHanyuPinyinStringArray(newChar[i], defaultFormat)[0];
                } catch (BadHanyuPinyinOutputFormatCombination e) {
                    e.printStackTrace();
                }
            } else {
                pinyinStr += newChar[i];
            }
            pinyinStr += "/";
        }
        return pinyinStr;
    }
}
