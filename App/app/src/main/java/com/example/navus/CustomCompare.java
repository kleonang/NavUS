package com.example.navus;

import java.util.Comparator;

public class CustomCompare implements Comparator<String> {
    private Comparator<String> comparator;

    public CustomCompare(Comparator<String> comparator) {
        this.comparator = comparator;
    }

    public int compare(String s1, String s2) {

        int s1length = s1.length();
        int s2length = s2.length();
        int s1index = 0;
        int s2index = 0;

        while (s1index < s1length && s2index < s2length) {
            String firstblock = getsubstring(s1, s1length, s1index);
            String secondblock = getsubstring(s2, s2length, s2index);

            s1index += firstblock.length();
            s2index += secondblock.length();

            // If both block starts with a number, sort them numerically
            int result = 0;
            if (checkdigit(firstblock.charAt(0)) && checkdigit(secondblock.charAt(0))) {
                int firstblocklength = firstblock.length();
                result = firstblocklength - secondblock.length();

                // If both are of the same length, sort by second number and so on
                if (result == 0) {
                    for (int i = 0; i < firstblocklength; i++) {
                        result = firstblock.charAt(i) - secondblock.charAt(i);
                        //once there is a difference in digits, break
                        if (result != 0) {
                            return result;
                        }
                    }
                }
            } else {
                result = comparator.compare(firstblock, secondblock);
            }

            if (result != 0)
                return result;
        }

        return s1length - s2length;
    }

    //returns a substring until an alphabet changes to a digit and vice versa
    private final String getsubstring(String s, int length, int index) {
        String return_string = "";
        char c = s.charAt(index);
        return_string += c;
        index++;

        //if it is a digit
        if (checkdigit((c))) {
            while (index < length) {
                c = s.charAt(index);
                if (!checkdigit((c)))
                    break;
                return_string += c;
                index++;
            }
        } else { //not a digit
            while (index < length) {
                c = s.charAt(index);
                if (checkdigit(c))
                    break;
                return_string += c;
                index++;
            }
        }
        return return_string;
    }


    boolean checkdigit(char c) {
        return c >= 48 && c <= 57;
    }


}
