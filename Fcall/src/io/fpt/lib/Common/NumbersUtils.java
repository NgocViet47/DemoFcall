package io.fpt.lib.Common;

import android.text.TextUtils;

public class NumbersUtils {
    public static boolean isNum(String paste) {
        return paste.matches("^[0-9]+$");
    }

    //Kiểm tra có phải phone hợp lý không
    public final static boolean isValidPhone(CharSequence target) {
        if (TextUtils.isEmpty(target))
            return false;
        else if (!isNum(target.toString()))
            return false;
        else if (target.length() < 10)
            return false;
        else if (target.length() > 11)
            return false;
        else if (target.charAt(0) != '0')
            return false;
        else
            return true;
    }

    public static long parseLong(String text) {
        long number = 0;
        try {
            number = Long.parseLong(text);
        } catch (NumberFormatException ex) {
        }
        return number;
    }
}
