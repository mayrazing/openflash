const K = "http://openflash.local:5173", v = "openflash-import-root";
function j(e) {
  return e == null ? null : String(e);
}
function ee(e, t) {
  const n = j(t);
  return !!n && e.some((a) => String(a.id) === n);
}
function x(e, t) {
  const n = j(t);
  return n ? ee(e, n) ? { defaultDeckId: n, shouldClear: !1 } : { defaultDeckId: null, shouldClear: !0 } : { defaultDeckId: null, shouldClear: !1 };
}
const te = ["zh", "en", "fi", "de"], R = {
  en: {
    "popup.title": "OpenFlash Import",
    "popup.serviceUrl": "Service URL",
    "popup.login": "Log in",
    "popup.refresh": "Refresh",
    "popup.logout": "Log out",
    "popup.decksTitle": "Decks",
    "popup.deckName": "Deck name",
    "popup.createDeck": "Create",
    "popup.unsetDefaultDeck": "Unset default",
    "popup.setDefaultDeck": "Set default",
    "popup.deleteDeck": "Delete",
    "popup.deleteDeckConfirmTitle": "Delete deck",
    "popup.deleteDeckConfirmBody": 'Delete "{{deckName}}"? Its cards are deleted with it and cannot be restored.',
    "popup.cancel": "Cancel",
    "popup.selectedDeck": "Selected",
    "popup.defaultDeckSet": "Default deck set: right-click OpenFlash Import to import directly.",
    "popup.defaultDeckMissing": "No default deck: choose a deck when importing from the right-click menu.",
    "popup.aiPromptTitle": "AI prompt",
    "popup.aiPromptTitleWithDeck": "{{title}} · {{deckName}}",
    "popup.aiPluginRequired": "AI generation works only after the AI explanation plugin is added on the app home page.",
    "popup.aiPluginDisabled": "Plugin disabled",
    "popup.aiCompletionPrompt": "Other side completion prompt",
    "popup.aiCompletionPlaceholder": "Example: generate a concise meaning, example, or answer from the front side",
    "popup.aiCompletionEnabled": "Enable automatic other side generation",
    "popup.saveAi": "Save prompt",
    "popup.saved": "Saved",
    "menu.title": "OpenFlash Import",
    "menu.defaultDeckUnavailable": "Default deck unavailable. Please set it again.",
    "menu.defaultDeckRequired": "Set a default deck in the extension settings first.",
    "menu.noAvailablePage": "No available page",
    "import.emptyContent": "No content to import",
    "import.selectionReadFailed": "Could not read current selection",
    "import.missingReceiver": "This page doesn't support extension import. Please refresh or try a regular web page.",
    "import.success": "Imported to OpenFlash",
    "import.partialImageFailed": "Imported, {{count}} image(s) failed",
    "import.failed": "Import failed",
    "notification.saved": "Saved",
    "notification.partialSaved": "Saved, {{count}} image(s) failed",
    "errors.40010": "Card already exists",
    "errors.40090": "Image URL is invalid",
    "errors.40091": "No importable content",
    "errors.40092": "Image import failed",
    "errors.40101": "Please log in first",
    "errors.40102": "Session expired — please log in again",
    "errors.50301": "This feature is not yet available",
    "manualCard.title": "Quick manual card",
    "manualCard.sideA": "Side A",
    "manualCard.sideB": "Side B",
    "manualCard.save": "Save",
    "manualCard.saving": "Saving...",
    "manualCard.cancel": "Cancel",
    "manualCard.unsavedTitle": "Unsaved content",
    "manualCard.unsavedConfirm": "Close",
    "manualCard.unsavedBack": "Keep editing",
    "manualCard.emptyContent": "Fill in at least one side or paste an image",
    "manualCard.imageProcessFailed": "Image processing failed",
    "manualCard.imageTooLarge": "Image is too large or still exceeds 5 MB after compression",
    "manualCard.imagesTooLarge": "Images must not exceed 20 MB in total",
    "manualCard.tooManyImages": "A card can contain at most 10 images",
    "manualCard.saveFailed": "Save failed, please retry",
    "manualCard.saved": "Saved",
    "manualCard.unsupportedPage": "Quick card is not supported on this page",
    "popup.shortcutsTitle": "Shortcuts",
    "popup.shortcutImportDefault": "Import selection directly",
    "popup.shortcutManualCard": "Quick manual card",
    "popup.shortcutUnset": "Not set",
    "popup.shortcutBrowserTip": "Shortcuts can be changed in the browser extension shortcuts page.",
    "popup.shortcutSettingsButton": "Set shortcuts",
    "popup.shortcutSettingsAction": "Set",
    "shortcutSetup.title": "Set OpenFlash shortcuts",
    "shortcutSetup.description": "OpenFlash can import selected text or create a quick card from any page. Set shortcuts in the browser shortcut settings page.",
    "shortcutSetup.importDefault": "Import selection directly",
    "shortcutSetup.manualCard": "Quick manual card",
    "shortcutSetup.openButton": "Open shortcut settings"
  },
  zh: {
    "popup.title": "OpenFlash 导入",
    "popup.serviceUrl": "服务地址",
    "popup.login": "登录",
    "popup.refresh": "刷新",
    "popup.logout": "退出登录",
    "popup.decksTitle": "卡包",
    "popup.deckName": "卡包名称",
    "popup.createDeck": "新建",
    "popup.unsetDefaultDeck": "取消默认",
    "popup.setDefaultDeck": "设为默认",
    "popup.deleteDeck": "删除",
    "popup.deleteDeckConfirmTitle": "删除卡包",
    "popup.deleteDeckConfirmBody": "确定删除卡包「{{deckName}}」吗？卡包内的卡片会一并删除，且无法恢复。",
    "popup.cancel": "取消",
    "popup.selectedDeck": "已选中",
    "popup.defaultDeckSet": "默认卡包已设置：右键点击 OpenFlash Import 将直接导入。",
    "popup.defaultDeckMissing": "未设置默认卡包：右键导入时请选择卡包。",
    "popup.aiPromptTitle": "AI 提示词",
    "popup.aiPromptTitleWithDeck": "{{title}} · {{deckName}}",
    "popup.aiPluginRequired": "AI 生成功能必须在 app 主页中加入 AI 解析插件才能起作用。",
    "popup.aiPluginDisabled": "插件未启用",
    "popup.aiCompletionPrompt": "另一面补全提示词",
    "popup.aiCompletionPlaceholder": "例如: 从正面内容生成简洁释义、例句或答案",
    "popup.aiCompletionEnabled": "自动补全另一面",
    "popup.saveAi": "保存提示词",
    "popup.saved": "已保存",
    "menu.title": "OpenFlash 导入",
    "menu.defaultDeckUnavailable": "默认卡包不可用，请重新设置",
    "menu.defaultDeckRequired": "请先在插件设置中设置默认卡包",
    "menu.noAvailablePage": "无可用页面",
    "import.emptyContent": "没有可导入内容",
    "import.selectionReadFailed": "无法读取当前选区",
    "import.missingReceiver": "当前页面不支持插件导入，请刷新页面或换普通网页重试",
    "import.success": "已导入 OpenFlash",
    "import.partialImageFailed": "已导入，{{count}} 张图片失败",
    "import.failed": "导入失败",
    "notification.saved": "已保存",
    "notification.partialSaved": "已保存，{{count}} 张图片失败",
    "errors.40010": "卡片已存在",
    "errors.40090": "图片地址不合法",
    "errors.40091": "没有可导入内容",
    "errors.40092": "图片导入失败",
    "errors.40101": "请先登录",
    "errors.40102": "登录状态已失效，请重新登录",
    "errors.50301": "该功能暂未开放",
    "manualCard.title": "快速手动建卡",
    "manualCard.sideA": "A 面",
    "manualCard.sideB": "B 面",
    "manualCard.save": "保存",
    "manualCard.saving": "保存中...",
    "manualCard.cancel": "取消",
    "manualCard.unsavedTitle": "内容尚未保存",
    "manualCard.unsavedConfirm": "关闭",
    "manualCard.unsavedBack": "继续编辑",
    "manualCard.emptyContent": "至少填写一面或粘贴一张图片",
    "manualCard.imageProcessFailed": "图片处理失败",
    "manualCard.imageTooLarge": "图片过大，或压缩后仍超过 5 MB",
    "manualCard.imagesTooLarge": "所有图片合计不能超过 20 MB",
    "manualCard.tooManyImages": "一张卡片最多添加 10 张图片",
    "manualCard.saveFailed": "保存失败，请重试",
    "manualCard.saved": "已保存",
    "manualCard.unsupportedPage": "当前页面不支持快速建卡",
    "popup.shortcutsTitle": "快捷键",
    "popup.shortcutImportDefault": "直接导入选中内容",
    "popup.shortcutManualCard": "快速手动建卡",
    "popup.shortcutUnset": "未设置",
    "popup.shortcutBrowserTip": "可在浏览器扩展快捷键设置页修改快捷键。",
    "popup.shortcutSettingsButton": "设置快捷键",
    "popup.shortcutSettingsAction": "设置",
    "shortcutSetup.title": "设置 OpenFlash 快捷键",
    "shortcutSetup.description": "OpenFlash 可以从任意网页直接导入选中文字，也可以快速手动建卡。请在浏览器快捷键设置页绑定快捷键。",
    "shortcutSetup.importDefault": "直接导入选中内容",
    "shortcutSetup.manualCard": "快速手动建卡",
    "shortcutSetup.openButton": "打开快捷键设置"
  },
  fi: {
    "popup.serviceUrl": "Palvelimen osoite",
    "popup.login": "Kirjaudu",
    "popup.title": "OpenFlash-tuonti",
    "popup.refresh": "Päivitä",
    "popup.logout": "Kirjaudu ulos",
    "popup.decksTitle": "Pakat",
    "popup.deckName": "Pakan nimi",
    "popup.createDeck": "Luo",
    "popup.unsetDefaultDeck": "Poista oletus",
    "popup.setDefaultDeck": "Aseta oletukseksi",
    "popup.deleteDeck": "Poista",
    "popup.deleteDeckConfirmTitle": "Poista pakka",
    "popup.deleteDeckConfirmBody": 'Poistetaanko pakka "{{deckName}}"? Sen kortit poistetaan samalla, eikä niitä voi palauttaa.',
    "popup.cancel": "Peruuta",
    "popup.selectedDeck": "Valittu",
    "popup.defaultDeckSet": "Oletuspakka asetettu: tuo suoraan napsauttamalla OpenFlash Importia hiiren oikealla painikkeella.",
    "popup.defaultDeckMissing": "Oletuspakkaa ei ole asetettu: valitse pakka hiiren oikean painikkeen tuonnissa.",
    "popup.aiPromptTitle": "AI-kehote",
    "popup.aiPromptTitleWithDeck": "{{title}} · {{deckName}}",
    "popup.aiPluginRequired": "AI-luonti toimii vain, kun AI-selitysliitännäinen on lisätty sovelluksen etusivulla.",
    "popup.aiPluginDisabled": "Liitännäinen ei käytössä",
    "popup.aiCompletionPrompt": "Toisen puolen täydennyskehote",
    "popup.aiCompletionPlaceholder": "Esimerkki: luo etupuolen perusteella tiivis merkitys, esimerkki tai vastaus",
    "popup.aiCompletionEnabled": "Ota toisen puolen automaattinen luonti käyttöön",
    "popup.saveAi": "Tallenna kehote",
    "popup.saved": "Tallennettu",
    "menu.title": "OpenFlash-tuonti",
    "menu.defaultDeckUnavailable": "Oletuspakka ei ole käytettävissä. Aseta se uudelleen.",
    "menu.defaultDeckRequired": "Aseta ensin oletuspakka laajennuksen asetuksissa.",
    "menu.noAvailablePage": "Sivua ei ole käytettävissä",
    "import.emptyContent": "Ei tuotavaa sisältöä",
    "import.selectionReadFailed": "Nykyistä valintaa ei voitu lukea",
    "import.missingReceiver": "Tämä sivu ei tue laajennustuontia. Päivitä sivu tai kokeile tavallista verkkosivua.",
    "import.success": "Tuotu OpenFlashiin",
    "import.partialImageFailed": "Tuotu, {{count}} kuvan tuonti epäonnistui",
    "import.failed": "Tuonti epäonnistui",
    "notification.saved": "Tallennettu",
    "notification.partialSaved": "Tallennettu, {{count}} kuvan tallennus epäonnistui",
    "errors.40010": "Kortti on jo olemassa",
    "errors.40090": "Virheellinen kuvan URL",
    "errors.40091": "Ei tuotavaa sisältöä",
    "errors.40092": "Kuvan tuonti epäonnistui",
    "errors.40101": "Kirjaudu ensin",
    "errors.40102": "Kirjautuminen vanheni. Kirjaudu uudelleen",
    "errors.50301": "Toiminto on poistettu käytöstä",
    "manualCard.title": "Nopea manuaalikortti",
    "manualCard.sideA": "Puoli A",
    "manualCard.sideB": "Puoli B",
    "manualCard.save": "Tallenna",
    "manualCard.saving": "Tallennetaan...",
    "manualCard.cancel": "Peruuta",
    "manualCard.unsavedTitle": "Tallentamaton sisältö",
    "manualCard.unsavedConfirm": "Sulje",
    "manualCard.unsavedBack": "Jatka muokkausta",
    "manualCard.emptyContent": "Täytä vähintään toinen puoli tai liitä kuva",
    "manualCard.imageProcessFailed": "Kuvan käsittely epäonnistui",
    "manualCard.imageTooLarge": "Kuva on liian suuri tai ylittää pakkauksen jälkeen 5 Mt",
    "manualCard.imagesTooLarge": "Kuvien yhteiskoko saa olla enintään 20 Mt",
    "manualCard.tooManyImages": "Kortissa voi olla enintään 10 kuvaa",
    "manualCard.saveFailed": "Tallennus epäonnistui, yritä uudelleen",
    "manualCard.saved": "Tallennettu",
    "manualCard.unsupportedPage": "Pikakorttia ei tueta tällä sivulla",
    "popup.shortcutsTitle": "Pikanäppäimet",
    "popup.shortcutImportDefault": "Tuo valinta suoraan",
    "popup.shortcutManualCard": "Nopea manuaalikortti",
    "popup.shortcutUnset": "Ei asetettu",
    "popup.shortcutBrowserTip": "Pikanäppäimiä voi muuttaa selaimen laajennusten pikanäppäinsivulla.",
    "popup.shortcutSettingsButton": "Aseta pikanäppäimet",
    "popup.shortcutSettingsAction": "Aseta",
    "shortcutSetup.title": "Aseta OpenFlash-pikanäppäimet",
    "shortcutSetup.description": "OpenFlash voi tuoda valitun tekstin tai luoda pikakortin miltä tahansa sivulta. Aseta pikanäppäimet selaimen asetussivulla.",
    "shortcutSetup.importDefault": "Tuo valinta suoraan",
    "shortcutSetup.manualCard": "Nopea manuaalikortti",
    "shortcutSetup.openButton": "Avaa pikanäppäinasetukset"
  },
  de: {
    "popup.serviceUrl": "Service-Adresse",
    "popup.login": "Anmelden",
    "popup.title": "OpenFlash-Import",
    "popup.refresh": "Aktualisieren",
    "popup.logout": "Abmelden",
    "popup.decksTitle": "Decks",
    "popup.deckName": "Deckname",
    "popup.createDeck": "Erstellen",
    "popup.unsetDefaultDeck": "Standard entfernen",
    "popup.setDefaultDeck": "Als Standard setzen",
    "popup.deleteDeck": "Löschen",
    "popup.deleteDeckConfirmTitle": "Deck löschen",
    "popup.deleteDeckConfirmBody": 'Deck "{{deckName}}" löschen? Die enthaltenen Karten werden mitgelöscht und lassen sich nicht wiederherstellen.',
    "popup.cancel": "Abbrechen",
    "popup.selectedDeck": "Ausgewählt",
    "popup.defaultDeckSet": "Standarddeck festgelegt: Rechtsklick auf OpenFlash Import importiert direkt.",
    "popup.defaultDeckMissing": "Kein Standarddeck: Wähle beim Rechtsklick-Import ein Deck aus.",
    "popup.aiPromptTitle": "AI-Prompt",
    "popup.aiPromptTitleWithDeck": "{{title}} · {{deckName}}",
    "popup.aiPluginRequired": "AI-Erzeugung funktioniert nur, wenn das AI-Erklärungs-Plugin auf der App-Startseite hinzugefügt wurde.",
    "popup.aiPluginDisabled": "Plugin deaktiviert",
    "popup.aiCompletionPrompt": "Prompt zum Ergänzen der anderen Seite",
    "popup.aiCompletionPlaceholder": "Beispiel: Aus der Vorderseite eine kurze Bedeutung, ein Beispiel oder eine Antwort erzeugen",
    "popup.aiCompletionEnabled": "Automatische Erzeugung der anderen Seite aktivieren",
    "popup.saveAi": "Prompt speichern",
    "popup.saved": "Gespeichert",
    "menu.title": "OpenFlash-Import",
    "menu.defaultDeckUnavailable": "Standarddeck nicht verfügbar. Bitte erneut festlegen.",
    "menu.defaultDeckRequired": "Lege zuerst in den Erweiterungseinstellungen ein Standarddeck fest.",
    "menu.noAvailablePage": "Keine verfügbare Seite",
    "import.emptyContent": "Kein Inhalt zum Importieren",
    "import.selectionReadFailed": "Aktuelle Auswahl konnte nicht gelesen werden",
    "import.missingReceiver": "Diese Seite unterstützt keinen Erweiterungsimport. Bitte aktualisieren Sie die Seite oder versuchen Sie eine normale Webseite.",
    "import.success": "In OpenFlash importiert",
    "import.partialImageFailed": "Importiert, {{count}} Bild(er) fehlgeschlagen",
    "import.failed": "Import fehlgeschlagen",
    "notification.saved": "Gespeichert",
    "notification.partialSaved": "Gespeichert, {{count}} Bild(er) fehlgeschlagen",
    "errors.40010": "Karte existiert bereits",
    "errors.40090": "Ungültige Bild-URL",
    "errors.40091": "Kein Inhalt zum Importieren",
    "errors.40092": "Bildimport fehlgeschlagen",
    "errors.40101": "Bitte zuerst anmelden",
    "errors.40102": "Anmeldung abgelaufen. Bitte erneut anmelden",
    "errors.50301": "Funktion ist deaktiviert",
    "manualCard.title": "Schnelle manuelle Karte",
    "manualCard.sideA": "Seite A",
    "manualCard.sideB": "Seite B",
    "manualCard.save": "Speichern",
    "manualCard.saving": "Wird gespeichert...",
    "manualCard.cancel": "Abbrechen",
    "manualCard.unsavedTitle": "Inhalt nicht gespeichert",
    "manualCard.unsavedConfirm": "Schließen",
    "manualCard.unsavedBack": "Weiter bearbeiten",
    "manualCard.emptyContent": "Mindestens eine Seite ausfüllen oder ein Bild einfügen",
    "manualCard.imageProcessFailed": "Bildverarbeitung fehlgeschlagen",
    "manualCard.imageTooLarge": "Bild ist zu groß oder nach der Komprimierung noch größer als 5 MB",
    "manualCard.imagesTooLarge": "Bilder dürfen zusammen höchstens 20 MB groß sein",
    "manualCard.tooManyImages": "Eine Karte darf höchstens 10 Bilder enthalten",
    "manualCard.saveFailed": "Speichern fehlgeschlagen, bitte erneut versuchen",
    "manualCard.saved": "Gespeichert",
    "manualCard.unsupportedPage": "Schnellkarte wird auf dieser Seite nicht unterstützt",
    "popup.shortcutsTitle": "Tastenkürzel",
    "popup.shortcutImportDefault": "Auswahl direkt importieren",
    "popup.shortcutManualCard": "Schnelle manuelle Karte",
    "popup.shortcutUnset": "Nicht festgelegt",
    "popup.shortcutBrowserTip": "Tastenkürzel können auf der Kürzelseite der Browsererweiterungen geändert werden.",
    "popup.shortcutSettingsButton": "Tastenkürzel festlegen",
    "popup.shortcutSettingsAction": "Festlegen",
    "shortcutSetup.title": "OpenFlash-Tastenkürzel festlegen",
    "shortcutSetup.description": "OpenFlash kann markierten Text direkt importieren oder eine Schnellkarte von jeder Seite erstellen. Lege die Tastenkürzel auf der Kürzelseite des Browsers fest.",
    "shortcutSetup.importDefault": "Auswahl direkt importieren",
    "shortcutSetup.manualCard": "Schnelle manuelle Karte",
    "shortcutSetup.openButton": "Tastenkürzel-Einstellungen öffnen"
  }
};
let L = "en";
function ae(e) {
  return L = te.includes(e) ? e : "en", L;
}
function z() {
  L = "en";
}
function h(e, t) {
  var r;
  const n = t ?? {}, a = ((r = R[L]) == null ? void 0 : r[e]) ?? R.en[e] ?? e;
  return Object.entries(n).reduce(
    (i, [u, o]) => i.replaceAll(`{{${u}}}`, String(o)),
    a
  );
}
function ne(e, t) {
  return String(e || "") === v && t || null;
}
function re(e) {
  let t = null, n = !1, a = null;
  async function r() {
    return t ? (n = !0, t) : (t = (async () => {
      let o = null;
      do {
        n = !1;
        try {
          await i(), o = null;
        } catch (l) {
          o = l;
        }
      } while (n);
      if (o) throw o;
    })().finally(() => {
      t = null;
    }), t);
  }
  async function i() {
    await e.contextMenus.removeAll();
    let o = [];
    try {
      const l = await e.getServiceUrl();
      o = await e.api.decks(l);
      const d = Array.isArray(o) ? x(o, await e.storage.getDefaultDeckId()) : { defaultDeckId: null, shouldClear: !1 };
      a = d.defaultDeckId, d.shouldClear && await e.storage.setDefaultDeckId(null);
    } catch (l) {
      throw a = null, l;
    }
    e.contextMenus.create({
      id: v,
      title: h("menu.title"),
      contexts: ["all"]
    });
  }
  async function u(o, l) {
    let d = (o == null ? void 0 : o.menuItemId) === v ? a : null;
    if ((o == null ? void 0 : o.menuItemId) === v && !d) {
      const g = await e.storage.getDefaultDeckId();
      if (g) {
        const D = await e.getServiceUrl(), s = await e.api.decks(D), p = Array.isArray(s) ? x(s, g) : { defaultDeckId: null, shouldClear: !1 };
        if (p.shouldClear) {
          await e.storage.setDefaultDeckId(null), await e.notify(h("menu.defaultDeckUnavailable"), "warning", l == null ? void 0 : l.id);
          return;
        }
        a = p.defaultDeckId, d = a;
      }
    }
    if ((o == null ? void 0 : o.menuItemId) === v && !d) {
      await e.notify(h("menu.defaultDeckRequired"), "warning", l == null ? void 0 : l.id);
      return;
    }
    const m = ne(o == null ? void 0 : o.menuItemId, d);
    if (m) {
      if (!(l != null && l.id)) {
        await e.notify(h("menu.noAvailablePage"), "warning", l == null ? void 0 : l.id);
        return;
      }
      await e.importSelectionToDeck(l, m, o);
    }
  }
  return {
    currentDefaultDeckId: () => a,
    handleMenuClick: u,
    refreshMenus: r
  };
}
const ie = "openflash-import-default";
function oe(e) {
  return function(n, a) {
    return n !== ie ? Promise.resolve(!1) : e.handleMenuClick({ menuItemId: v }, a);
  };
}
const ue = "openflash-manual-card";
function le(e) {
  return async function(n, a) {
    if (n !== ue) return !1;
    if (!(a != null && a.id)) return !0;
    const r = await e.getServiceUrl(), i = await e.getDefaultDeckId();
    if (!i)
      return await e.notify(e.t("menu.defaultDeckRequired"), "error", a.id), !0;
    if (await e.ensureBrowserImportEnabled(r), !(await e.listDecks(r)).some((l) => String(l.id) === String(i)))
      return await e.setDefaultDeckId(null), await e.notify(e.t("menu.defaultDeckUnavailable"), "error", a.id), !0;
    let o = "";
    try {
      o = await e.readSelectedText(a.id);
    } catch {
    }
    return await e.openEditor({
      deckId: i,
      baseUrl: r,
      sourceTabId: a.id,
      labels: se(e.t),
      selectedText: o
    }), !0;
  };
}
function se(e) {
  return [
    "manualCard.title",
    "manualCard.sideA",
    "manualCard.sideB",
    "manualCard.save",
    "manualCard.saving",
    "manualCard.cancel",
    "manualCard.unsavedTitle",
    "manualCard.unsavedConfirm",
    "manualCard.unsavedBack",
    "manualCard.emptyContent",
    "manualCard.imageProcessFailed",
    "manualCard.imageTooLarge",
    "manualCard.imagesTooLarge",
    "manualCard.tooManyImages",
    "manualCard.saveFailed",
    "manualCard.saved"
  ].reduce((t, n) => (t[n] = e(n), t), {});
}
const P = {
  success: "#34c759",
  warning: "#ff8d28",
  error: "#ff383c"
}, ce = {
  success: P.success,
  warning: P.warning,
  error: P.error
};
function pe(e) {
  let t = null;
  async function n(r, i, u) {
    const o = {
      type: "OPENFLASH_SHOW_NOTIFICATION",
      message: i,
      level: u
    };
    try {
      await e.tabs.sendMessage(r, o);
    } catch (l) {
      if (!e.ensurePageReceiver) throw l;
      await e.ensurePageReceiver(r), await e.tabs.sendMessage(r, o);
    }
  }
  async function a(r, i = "success", u = null) {
    const o = [
      Promise.resolve().then(() => e.setLastImportStatus({ message: r, level: i, at: e.now() })),
      Promise.resolve().then(() => e.action.setBadgeText({ text: i === "success" ? "✓" : "!" })),
      Promise.resolve().then(() => e.action.setBadgeBackgroundColor({
        color: ce[i] || P.error
      }))
    ];
    u != null && o.push(Promise.resolve().then(() => n(u, r, i))), await Promise.allSettled(o), t !== null && e.clearTimeout(t), t = e.setTimeout(() => {
      e.action.setBadgeText({ text: "" }).catch(() => {
      }), t = null;
    }, 2500);
  }
  return a;
}
function de(e) {
  return async function(n, a = "success", r = null) {
    let i = r;
    if (!Number.isInteger(i) || i <= 0) {
      const [u] = await e.tabs.query({ active: !0, currentWindow: !0 });
      i = u == null ? void 0 : u.id;
    }
    await e.notify(n, a, i);
  };
}
const A = {
  serviceUrl: "serviceUrl",
  defaultDeckId: "defaultDeckId",
  lastImportStatus: "lastImportStatus"
};
async function M() {
  const e = await chrome.storage.local.get(A.serviceUrl);
  return G(e[A.serviceUrl] || K);
}
async function H() {
  return (await chrome.storage.local.get(A.defaultDeckId))[A.defaultDeckId] || null;
}
async function W(e) {
  await chrome.storage.local.set({ [A.defaultDeckId]: e == null ? null : String(e) });
}
async function me(e) {
  await chrome.storage.local.set({ [A.lastImportStatus]: e || null });
}
function G(e) {
  return String(e || K).trim().replace(/\/+$/, "");
}
function $(e, t) {
  return `${G(e)}${t}`;
}
function q(e) {
  const t = `errors.${e}`, n = h(t);
  return n === t ? `API error ${e ?? "unknown"}` : n;
}
function X(e) {
  if (!e || e.code !== 200) {
    const t = new Error(q(e == null ? void 0 : e.code));
    throw t.code = e == null ? void 0 : e.code, t;
  }
  return e.data ?? null;
}
async function f(e, t, n = {}) {
  const a = await fetch($(e, t), {
    signal: AbortSignal.timeout(15e3),
    ...n,
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
      ...n.headers ?? {}
    }
  }), r = await a.text();
  let i = null;
  try {
    i = r ? JSON.parse(r) : null;
  } catch {
  }
  if (a.status === 401) {
    const u = new Error(q((i == null ? void 0 : i.code) ?? 40101));
    throw u.code = (i == null ? void 0 : i.code) ?? 40101, u;
  }
  return X(i);
}
async function J(e, t) {
  const n = new FormData();
  n.append("file", t, "image.jpg");
  const r = await (await fetch($(e, "/api/upload"), {
    method: "POST",
    signal: AbortSignal.timeout(15e3),
    credentials: "include",
    body: n
  })).json(), i = X(r);
  return (i == null ? void 0 : i.url) ?? null;
}
async function V(e) {
  await f(e, "/api/browser-import/images/transfer", {
    method: "POST",
    body: JSON.stringify({ urls: [] })
  });
}
const y = {
  settings: (e) => f(e, "/api/settings"),
  me: (e) => f(e, "/api/auth/me"),
  logout: (e) => f(e, "/api/auth/logout", { method: "POST" }),
  decks: (e) => f(e, "/api/decks"),
  createDeck: (e, t) => f(e, "/api/decks", {
    method: "POST",
    body: JSON.stringify({ name: t })
  }),
  deleteDeck: (e, t) => f(e, `/api/decks/${t}`, { method: "DELETE" }),
  getAiSettings: (e, t) => f(e, `/api/decks/${t}/ai-settings`),
  saveAiSettings: (e, t, n) => f(e, `/api/decks/${t}/ai-settings`, {
    method: "PUT",
    body: JSON.stringify(n)
  }),
  transferImages: (e, t) => f(e, "/api/browser-import/images/transfer", {
    method: "POST",
    body: JSON.stringify({ urls: t })
  }),
  createImportedCard: (e, t, n) => f(e, `/api/browser-import/decks/${t}/cards`, {
    method: "POST",
    body: JSON.stringify(n)
  })
};
function he(e) {
  const t = {
    maxImageCount: 10,
    maxImageBytes: 5242880,
    maxTotalImageBytes: 20971520,
    ...e.imageLimits || {}
  };
  return async function(a, r) {
    if (!((a == null ? void 0 : a.type) === "OPENFLASH_MANUAL_CARD_UPLOAD_IMAGES" || (a == null ? void 0 : a.type) === "OPENFLASH_MANUAL_CARD_CREATE")) return !1;
    if (!e.isTrustedSender(r)) throw new Error("untrusted manual card sender");
    if ((a == null ? void 0 : a.type) === "OPENFLASH_MANUAL_CARD_UPLOAD_IMAGES")
      return ge(a, e, t);
    if ((a == null ? void 0 : a.type) === "OPENFLASH_MANUAL_CARD_CREATE")
      return { ok: !0, card: await e.createImportedCard(a.baseUrl, a.deckId, a.payload) };
  };
}
async function ge(e, t, n) {
  const a = [
    ...Array.isArray(e.sideAImages) ? e.sideAImages : [],
    ...Array.isArray(e.sideBImages) ? e.sideBImages : []
  ], r = fe(a, n), i = {};
  for (const u of r)
    i[u.source.id] = await t.uploadImageFile(
      e.baseUrl,
      Ce(u)
    );
  return { ok: !0, uploadedByImageId: i };
}
function fe(e, t) {
  if (e.length > t.maxImageCount)
    throw new Error("manualCard.tooManyImages");
  let n = 0;
  return e.map((a) => {
    const r = ke(a, t.maxImageBytes);
    if (n += r.byteLength, n > t.maxTotalImageBytes)
      throw new Error("manualCard.imagesTooLarge");
    return r;
  });
}
function ke(e, t) {
  const n = String((e == null ? void 0 : e.dataUrl) || ""), a = n.indexOf(","), r = a >= 0 ? n.slice(0, a) : "";
  if (!/^data:image\/[a-z0-9.+-]+;base64$/i.test(r))
    throw new Error("manualCard.imageProcessFailed");
  const i = n.length - a - 1, u = Math.ceil(t * 4 / 3) + 4;
  if (i <= 0 || i > u)
    throw new Error("manualCard.imageTooLarge");
  const o = n.endsWith("==") ? 2 : n.endsWith("=") ? 1 : 0, l = Math.floor(i * 3 / 4) - o;
  if (l > t)
    throw new Error("manualCard.imageTooLarge");
  return {
    source: e,
    mediaType: r.slice(5, -7),
    encoded: n.slice(a + 1),
    byteLength: l
  };
}
function Ce(e) {
  let t;
  try {
    t = atob(e.encoded);
  } catch {
    throw new Error("manualCard.imageProcessFailed");
  }
  const n = Uint8Array.from(t, (r) => r.charCodeAt(0)), a = new Blob([n], { type: e.mediaType || "image/jpeg" });
  return a.name = e.source.name || "image.jpg", a;
}
const Se = "manualCardContext:", E = "manualCardWindowId", we = 1e4, Ie = 1e3;
function ve(e) {
  return {
    async open(t) {
      const a = (await e.storageSession.get(E))[E];
      if (Number.isInteger(a))
        try {
          return await e.windows.update(a, { focused: !0 }), { reused: !0, windowId: a };
        } catch {
          await e.storageSession.remove(E);
        }
      const r = e.randomUUID(), i = `${Se}${r}`;
      await e.storageSession.set({ [i]: ye(t) });
      try {
        const u = await e.windows.create({
          focused: !0,
          height: 450,
          type: "popup",
          url: e.runtime.getURL(`manualCard.html?context=${encodeURIComponent(r)}`),
          width: 480
        });
        return Number.isInteger(u == null ? void 0 : u.id) && await e.storageSession.set({ [E]: u.id }), { reused: !1, windowId: u == null ? void 0 : u.id };
      } catch (u) {
        throw await e.storageSession.remove(i), u;
      }
    }
  };
}
function Ae(e, t) {
  if (!(e != null && e.url)) return !1;
  try {
    const n = new URL(t), a = new URL(e.url);
    return a.protocol === n.protocol && a.host === n.host && a.pathname === n.pathname;
  } catch {
    return !1;
  }
}
function ye(e) {
  if (!e || typeof e != "object") throw new Error("invalid manual card context");
  const t = Te(e.baseUrl), n = String(e.deckId || "").trim();
  if (!n || n.length > 128) throw new Error("invalid manual card deck");
  const a = e.sourceTabId;
  if (a != null && (!Number.isInteger(a) || a <= 0))
    throw new Error("invalid manual card source tab");
  const r = {};
  if (e.labels && typeof e.labels == "object")
    for (const [i, u] of Object.entries(e.labels))
      typeof u == "string" && (r[String(i).slice(0, 128)] = u.slice(0, Ie));
  return {
    baseUrl: t,
    deckId: n,
    ...a == null ? {} : { sourceTabId: a },
    labels: r,
    selectedText: typeof e.selectedText == "string" ? e.selectedText.slice(0, we) : ""
  };
}
function Te(e) {
  const t = new URL(String(e || ""));
  if (!["http:", "https:"].includes(t.protocol)) throw new Error("invalid manual card service URL");
  return t.href.replace(/\/$/, "");
}
const De = 8192, Ee = Math.ceil(8 * 1024 * 1024 * 4 / 3) + 1024, be = 20 * 1024 * 1024, Pe = 20, Le = 8 * 1024 * 1024, Me = 20 * 1024 * 1024, Be = 1e4;
function Oe(e, t) {
  return { sideAImage: e, failedCount: t };
}
async function Ue(e, t) {
  return _e(e, t, {
    transferImages: y.transferImages,
    fetchBlob: Ne,
    uploadImageFile: J
  });
}
async function _e(e, t, n) {
  const a = [];
  let r = 0;
  const i = Array.isArray(t) ? t : [];
  let u = 0;
  for (let s = 0; s < i.length; s += 1) {
    const p = i[s];
    if (!/^https?:\/\//i.test(p) && !/^(data|blob):/i.test(p)) continue;
    const S = /^https?:\/\//i.test(p) ? De : Ee;
    if (p.length > S) {
      r += 1;
      continue;
    }
    if (u + p.length > be) {
      r += 1;
      continue;
    }
    a.length < Pe ? (a.push({ source: p, index: s }), u += p.length) : r += 1;
  }
  const o = a.filter(({ source: s }) => /^https?:\/\//i.test(s)), l = a.filter(({ source: s }) => /^(data|blob):/i.test(s)), d = /* @__PURE__ */ new Map();
  let m = r;
  if (o.length > 0) {
    const s = o.map(({ source: p }) => p);
    try {
      const S = (await n.transferImages(e, s)).results || [];
      for (let k = 0; k < o.length; k += 1) {
        const w = o[k], c = S[k] || { sourceUrl: w.source, success: !1 };
        c.success && c.url ? d.set(w.index, c.url) : m += 1;
      }
    } catch {
      m += o.length;
    }
  }
  let g = 0;
  for (const s of l)
    try {
      const p = Me - g, S = await Fe(
        e,
        s.source,
        n,
        Math.min(Le, p)
      );
      g += S.bytes, d.set(s.index, S.url);
    } catch {
      m += 1;
    }
  const D = a.map(({ index: s }) => d.get(s)).filter(Boolean);
  return Oe(D, m);
}
async function Fe(e, t, n, a) {
  if (!/^(data|blob):/i.test(t) || a <= 0)
    throw new Error("unsupported local image source");
  if (/^data:/i.test(t) && t.length > Math.ceil(a * 4 / 3) + 1024)
    throw new Error("local image exceeds size limit");
  const r = await n.fetchBlob(t, a);
  if (!r || !Number.isFinite(r.size) || r.size > a)
    throw new Error("local image exceeds size limit");
  const i = typeof File == "function" ? new File([r], "image.jpg", { type: r.type || "image/jpeg" }) : r;
  return { url: await n.uploadImageFile(e, i), bytes: r.size };
}
async function Ne(e, t) {
  var r;
  if (!/^(data|blob):/i.test(e)) throw new Error("remote fetch is forbidden");
  const n = new AbortController(), a = setTimeout(() => n.abort(), Be);
  try {
    const i = await fetch(e, { signal: n.signal });
    if (!i.ok) throw new Error("local image fetch failed");
    const u = Number(i.headers.get("content-length"));
    if (Number.isFinite(u) && u > t)
      throw new Error("local image exceeds size limit");
    if (!((r = i.body) != null && r.getReader)) {
      const m = await i.blob();
      if (m.size > t) throw new Error("local image exceeds size limit");
      return m;
    }
    const o = i.body.getReader(), l = [];
    let d = 0;
    for (; ; ) {
      const { done: m, value: g } = await o.read();
      if (m) break;
      if (d += g.byteLength, d > t)
        throw await o.cancel(), new Error("local image exceeds size limit");
      l.push(g);
    }
    return new Blob(l, { type: i.headers.get("content-type") || "application/octet-stream" });
  } finally {
    clearTimeout(a);
  }
}
function O(e, t) {
  const n = (e == null ? void 0 : e.sideA) || "", a = Array.isArray(e == null ? void 0 : e.imageSources) ? e.imageSources : [], r = Y(t);
  return !r || a.includes(r) ? { sideA: n, imageSources: a } : { sideA: n, imageSources: [r, ...a] };
}
async function xe(e, t, n) {
  try {
    return O(await t(), e);
  } catch (a) {
    if (n)
      try {
        return O(await n(), e);
      } catch {
      }
    if (Y(e))
      return O({ sideA: "", imageSources: [] }, e);
    throw Re(a) ? new Error(h("import.missingReceiver")) : a;
  }
}
function Re(e) {
  return String((e == null ? void 0 : e.message) || e).includes("Receiving end does not exist");
}
function Y(e) {
  return (e == null ? void 0 : e.mediaType) === "image" ? String(e.srcUrl || "").trim() : "";
}
function ze(e, { runtime: t, tabs: n }) {
  (e == null ? void 0 : e.reason) === "install" && n.create({ url: t.getURL("shortcutSetup.html") });
}
let b = null;
async function Ke(e) {
  await chrome.scripting.executeScript({
    target: { tabId: e },
    files: ["assets/contentScript.js"]
  });
}
const T = pe({
  setLastImportStatus: me,
  action: chrome.action,
  tabs: chrome.tabs,
  ensurePageReceiver: Ke,
  now: Date.now,
  setTimeout: globalThis.setTimeout.bind(globalThis),
  clearTimeout: globalThis.clearTimeout.bind(globalThis)
}), je = de({ tabs: chrome.tabs, notify: T });
async function He() {
  z();
  try {
    const e = await M(), t = await y.settings(e);
    ae(t == null ? void 0 : t.language);
  } catch {
    z();
  }
}
function U() {
  return b || (b = He().finally(() => {
    b = null;
  })), b;
}
const _ = re({
  contextMenus: chrome.contextMenus,
  storage: {
    getDefaultDeckId: H,
    setDefaultDeckId: W
  },
  api: y,
  getServiceUrl: M,
  importSelectionToDeck: Xe,
  notify: T
});
chrome.runtime.onInstalled.addListener((e) => {
  F().catch(() => Q()), ze(e, { runtime: chrome.runtime, tabs: chrome.tabs });
});
chrome.runtime.onStartup.addListener(() => {
  F().catch(() => Q());
});
chrome.runtime.onMessage.addListener((e, t, n) => (e == null ? void 0 : e.type) === "OPENFLASH_MANUAL_CARD_UPLOAD_IMAGES" || (e == null ? void 0 : e.type) === "OPENFLASH_MANUAL_CARD_CREATE" ? (qe(e, t).then((a) => n(a)).catch((a) => n({ ok: !1, message: a.message, code: a.code })), !0) : (e == null ? void 0 : e.type) === "OPENFLASH_NOTIFY_ACTIVE_TAB" ? (je(
  e.message,
  e.level,
  Z(t) ? e.sourceTabId : null
).then(() => n({ ok: !0 })).catch((a) => n({ ok: !1, message: a.message })), !0) : (e == null ? void 0 : e.type) !== "OPENFLASH_REFRESH_MENUS" ? !1 : (F().then(() => n({ ok: !0 })).catch((a) => {
  n({ ok: !1, message: a.message });
}), !0));
chrome.contextMenus.onClicked.addListener((e, t) => {
  U().then(() => _.handleMenuClick(e, t)).catch((n) => T(n.message || h("import.failed"), "error", t == null ? void 0 : t.id));
});
const We = oe(_), Ge = ve({
  storageSession: chrome.storage.session,
  windows: chrome.windows,
  runtime: chrome.runtime,
  randomUUID: () => crypto.randomUUID()
}), $e = le({
  getServiceUrl: M,
  getDefaultDeckId: H,
  setDefaultDeckId: W,
  ensureBrowserImportEnabled: V,
  listDecks: y.decks,
  readSelectedText: Ye,
  openEditor: Ge.open,
  notify: T,
  t: h
}), qe = he({
  uploadImageFile: J,
  createImportedCard: y.createImportedCard,
  isTrustedSender: Z
});
chrome.commands.onCommand.addListener((e, t) => {
  U().then(async () => {
    await $e(e, t) || await We(e, t);
  }).catch((n) => T(n.message || h("import.failed"), "error", t == null ? void 0 : t.id));
});
async function F() {
  return await U(), _.refreshMenus();
}
function Q() {
  chrome.contextMenus.removeAll(() => {
    chrome.contextMenus.create({
      id: v,
      title: h("menu.title"),
      contexts: ["all"],
      enabled: !1
    });
  });
}
async function Xe(e, t, n) {
  const a = await M();
  await V(a);
  const r = await xe(
    n,
    () => Je(e.id),
    () => Ve(e.id)
  ), { sideAImage: i, failedCount: u } = await Ue(a, r.imageSources), o = r.sideA || "";
  if (!o.trim() && i.length === 0)
    throw new Error(h("import.emptyContent"));
  await y.createImportedCard(a, t, { sideA: o, sideAImage: i }), await T(
    u > 0 ? h("notification.partialSaved", { count: u }) : h("notification.saved"),
    u > 0 ? "warning" : "success",
    e.id
  );
}
async function Je(e) {
  const t = await chrome.tabs.sendMessage(e, { type: "OPENFLASH_EXTRACT_SELECTION" });
  if (!(t != null && t.ok))
    throw new Error(h("import.selectionReadFailed"));
  return t.selection || { sideA: "", imageSources: [] };
}
async function Ve(e) {
  const [t] = await chrome.scripting.executeScript({
    target: { tabId: e },
    func: Ze
  });
  return (t == null ? void 0 : t.result) || { sideA: "", imageSources: [] };
}
async function Ye(e) {
  const [t] = await chrome.scripting.executeScript({
    target: { tabId: e },
    func: Qe
  });
  return typeof (t == null ? void 0 : t.result) == "string" ? t.result : "";
}
function Qe() {
  var e, t, n;
  return String(((n = (t = (e = window.getSelection) == null ? void 0 : e.call(window)) == null ? void 0 : t.toString) == null ? void 0 : n.call(t)) || "").slice(0, 1e4);
}
function Z(e) {
  return Ae(e, chrome.runtime.getURL("manualCard.html"));
}
function Ze() {
  var s, p, S;
  const e = window.getSelection();
  if (!e || e.rangeCount === 0)
    return { sideA: "", imageSources: [] };
  const t = 20, n = 1e5, a = 8192, r = Math.ceil(8 * 1024 * 1024 * 4 / 3) + 1024, i = 20 * 1024 * 1024, u = 1e4, o = String(((s = e.toString) == null ? void 0 : s.call(e)) || "").replace(/\s+/g, " ").trim().slice(0, n), l = [], d = /* @__PURE__ */ new Set();
  let m = 0, g = 0;
  e: for (let k = 0; k < e.rangeCount; k += 1) {
    const w = e.getRangeAt(k);
    let c = w.commonAncestorContainer;
    if ((c == null ? void 0 : c.nodeType) === 3 && (c = c.parentElement), !c) continue;
    const N = document.createTreeWalker(c, ((p = globalThis.NodeFilter) == null ? void 0 : p.SHOW_ELEMENT) || 1);
    let C = c.nodeType === 1 ? c : N.nextNode();
    for (; C; ) {
      if (m += 1, m > u) break e;
      if (String(C.tagName || "").toLowerCase() === "img" && !d.has(C)) {
        d.add(C);
        let B = !1;
        try {
          B = w.intersectsNode(C);
        } catch {
          B = !1;
        }
        if (B) {
          const I = D(
            C.currentSrc || ((S = C.getAttribute) == null ? void 0 : S.call(C, "src")) || "",
            document.baseURI
          );
          if (/^https?:\/\//i.test(I) ? I.length <= a : /^(data|blob):/i.test(I) && I.length <= r) {
            if (g + I.length > i) break e;
            l.push(I), g += I.length;
          }
          if (l.length >= t) break e;
        }
      }
      C = N.nextNode();
    }
  }
  return { sideA: o, imageSources: l };
  function D(k, w) {
    const c = String(k || "").trim();
    if (!c || /^(data|blob):/i.test(c))
      return c;
    try {
      return w ? new URL(c, w).href : c;
    } catch {
      return c;
    }
  }
}
