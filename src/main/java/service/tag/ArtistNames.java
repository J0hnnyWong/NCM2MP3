package service.tag;

import java.util.ArrayList;
import java.util.List;

/**
 * 艺人名称列表工具:NCM元数据中的 artist 是 [名称, 艺人ID] 的二维数组,
 * 目录模式下的艺人目录名用逗号分隔多人
 *
 * @author charlottexiao
 */
public class ArtistNames {

    /**
     * 从NCM元数据的 artist 数组中取出全部艺人名(丢弃ID与空项)
     *
     * @param artist 二维数组,每项为 [名称, 艺人ID]
     * @return 艺人名列表,永不为null
     */
    public static String[] of(String[][] artist) {
        List<String> names = new ArrayList<>();
        if (artist != null) {
            for (String[] entry : artist) {
                if (entry != null && entry.length > 0 && !isBlank(entry[0])) {
                    names.add(entry[0].trim());
                }
            }
        }
        return names.toArray(new String[0]);
    }

    /**
     * 目录名形式的艺人列表,如 "Eminem,Alicia Keys"
     *
     * @param pathSegment 目录名
     * @return 艺人名列表,永不为null
     */
    public static String[] fromPathSegment(String pathSegment) {
        List<String> names = new ArrayList<>();
        if (!isBlank(pathSegment)) {
            for (String name : pathSegment.split(",")) {
                if (!isBlank(name)) {
                    names.add(name.trim());
                }
            }
        }
        return names.toArray(new String[0]);
    }

    /**
     * 署名形式的名单,如 "Golden Landis Von Jones/Omer Fedi"。
     * 只能用斜杠分隔:名单内部允许出现 "Jefferson, Melissa" 这种带姓名字序逗号的单人写法
     *
     * @param credit 署名文本
     * @return 人名列表,永不为null
     */
    public static String[] fromCredit(String credit) {
        List<String> names = new ArrayList<>();
        if (!isBlank(credit)) {
            for (String name : credit.split("/")) {
                if (!isBlank(name)) {
                    names.add(name.trim());
                }
            }
        }
        return names.toArray(new String[0]);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
