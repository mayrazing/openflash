package openflash_core.entity;

/**
 * 描述页面可选择的练习模式。
 */
public class PracticeModeOption {
    private String value;
    private String label;

    /**
     * 创建空练习模式选项，供框架反序列化和 MyBatis 映射使用。
     */
    public PracticeModeOption() {
    }

    /**
     * 用模式值和显示文案创建练习模式选项。
     */
    public PracticeModeOption(String value, String label) {
        this.value = value;
        this.label = label;
    }

    /**
     * 返回练习模式值。
     */
    public String getValue() {
        return value;
    }

    /**
     * 设置练习模式值。
     */
    public void setValue(String value) {
        this.value = value;
    }

    /**
     * 返回页面显示文案。
     */
    public String getLabel() {
        return label;
    }

    /**
     * 设置页面显示文案。
     */
    public void setLabel(String label) {
        this.label = label;
    }
}
