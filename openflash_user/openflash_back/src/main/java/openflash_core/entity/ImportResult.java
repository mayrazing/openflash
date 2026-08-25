package openflash_core.entity;

/**
 * 返回一次导入的结果摘要。
 */
public class ImportResult {

    private Integer deckCount;
    private Integer cardCount;
    private Boolean settingsImported;

    public Integer getDeckCount() {
        return deckCount;
    }

    public void setDeckCount(Integer deckCount) {
        this.deckCount = deckCount;
    }

    public Integer getCardCount() {
        return cardCount;
    }

    public void setCardCount(Integer cardCount) {
        this.cardCount = cardCount;
    }

    public Boolean getSettingsImported() {
        return settingsImported;
    }

    public void setSettingsImported(Boolean settingsImported) {
        this.settingsImported = settingsImported;
    }
}
