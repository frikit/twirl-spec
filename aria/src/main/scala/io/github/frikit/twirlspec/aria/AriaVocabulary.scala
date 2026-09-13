/*
 * Copyright 2026 frikiT
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.frikit.twirlspec.aria

/** The parts of the ARIA specification a static checker can hold a page to.
  *
  * Names, values and relationships are all in the markup, so they need no
  * browser: what an assistive technology does with `aria-expanded="yes"` is
  * decided before anything is painted.
  */
object AriaVocabulary {

  /** Every attribute in ARIA 1.2. */
  val attributes: Set[String] = Set(
    "aria-activedescendant",
    "aria-atomic",
    "aria-autocomplete",
    "aria-braillelabel",
    "aria-brailleroledescription",
    "aria-busy",
    "aria-checked",
    "aria-colcount",
    "aria-colindex",
    "aria-colindextext",
    "aria-colspan",
    "aria-controls",
    "aria-current",
    "aria-describedby",
    "aria-description",
    "aria-details",
    "aria-disabled",
    "aria-dropeffect",
    "aria-errormessage",
    "aria-expanded",
    "aria-flowto",
    "aria-grabbed",
    "aria-haspopup",
    "aria-hidden",
    "aria-invalid",
    "aria-keyshortcuts",
    "aria-label",
    "aria-labelledby",
    "aria-level",
    "aria-live",
    "aria-modal",
    "aria-multiline",
    "aria-multiselectable",
    "aria-orientation",
    "aria-owns",
    "aria-placeholder",
    "aria-posinset",
    "aria-pressed",
    "aria-readonly",
    "aria-relevant",
    "aria-required",
    "aria-roledescription",
    "aria-rowcount",
    "aria-rowindex",
    "aria-rowindextext",
    "aria-rowspan",
    "aria-selected",
    "aria-setsize",
    "aria-sort",
    "aria-valuemax",
    "aria-valuemin",
    "aria-valuenow",
    "aria-valuetext"
  )

  /** Every role in ARIA 1.2, abstract roles excluded: those are not allowed in
    * markup.
    */
  val roles: Set[String] = Set(
    "alert",
    "alertdialog",
    "application",
    "article",
    "banner",
    "blockquote",
    "button",
    "caption",
    "cell",
    "checkbox",
    "code",
    "columnheader",
    "combobox",
    "complementary",
    "contentinfo",
    "definition",
    "deletion",
    "dialog",
    "document",
    "emphasis",
    "feed",
    "figure",
    "form",
    "generic",
    "grid",
    "gridcell",
    "group",
    "heading",
    "img",
    "insertion",
    "link",
    "list",
    "listbox",
    "listitem",
    "log",
    "main",
    "mark",
    "marquee",
    "math",
    "menu",
    "menubar",
    "menuitem",
    "menuitemcheckbox",
    "menuitemradio",
    "meter",
    "navigation",
    "none",
    "note",
    "option",
    "paragraph",
    "presentation",
    "progressbar",
    "radio",
    "radiogroup",
    "region",
    "row",
    "rowgroup",
    "rowheader",
    "scrollbar",
    "search",
    "searchbox",
    "separator",
    "slider",
    "spinbutton",
    "status",
    "strong",
    "subscript",
    "superscript",
    "switch",
    "tab",
    "table",
    "tablist",
    "tabpanel",
    "term",
    "textbox",
    "time",
    "timer",
    "toolbar",
    "tooltip",
    "tree",
    "treegrid",
    "treeitem"
  )

  /** Attributes whose value has to come from a fixed list. */
  val tokenValues: Map[String, Set[String]] = Map(
    "aria-atomic" -> Set("true", "false"),
    "aria-autocomplete" -> Set("inline", "list", "both", "none"),
    "aria-busy" -> Set("true", "false"),
    "aria-checked" -> Set("true", "false", "mixed", "undefined"),
    "aria-current" -> Set(
      "page",
      "step",
      "location",
      "date",
      "time",
      "true",
      "false"
    ),
    "aria-disabled" -> Set("true", "false"),
    "aria-expanded" -> Set("true", "false", "undefined"),
    "aria-haspopup" -> Set(
      "false",
      "true",
      "menu",
      "listbox",
      "tree",
      "grid",
      "dialog"
    ),
    "aria-hidden" -> Set("true", "false", "undefined"),
    "aria-invalid" -> Set("grammar", "false", "spelling", "true"),
    "aria-live" -> Set("assertive", "off", "polite"),
    "aria-modal" -> Set("true", "false"),
    "aria-multiline" -> Set("true", "false"),
    "aria-multiselectable" -> Set("true", "false"),
    "aria-orientation" -> Set("horizontal", "vertical", "undefined"),
    "aria-pressed" -> Set("true", "false", "mixed", "undefined"),
    "aria-readonly" -> Set("true", "false"),
    "aria-required" -> Set("true", "false"),
    "aria-selected" -> Set("true", "false", "undefined"),
    "aria-sort" -> Set("ascending", "descending", "none", "other")
  )

  /** Roles that mean nothing without these attributes. */
  val requiredAttributes: Map[String, Set[String]] = Map(
    "checkbox" -> Set("aria-checked"),
    "combobox" -> Set("aria-expanded"),
    "heading" -> Set("aria-level"),
    "menuitemcheckbox" -> Set("aria-checked"),
    "menuitemradio" -> Set("aria-checked"),
    "option" -> Set("aria-selected"),
    "radio" -> Set("aria-checked"),
    "scrollbar" -> Set("aria-valuenow"),
    "slider" -> Set("aria-valuenow"),
    "switch" -> Set("aria-checked")
  )

  /** Roles that are only meaningful inside one of these. */
  val requiredParents: Map[String, Set[String]] = Map(
    "columnheader" -> Set("row"),
    "gridcell" -> Set("row"),
    "listitem" -> Set("list", "group"),
    "menuitem" -> Set("menu", "menubar", "group"),
    "menuitemcheckbox" -> Set("menu", "menubar", "group"),
    "menuitemradio" -> Set("menu", "menubar", "group"),
    "option" -> Set("listbox", "group"),
    "row" -> Set("grid", "rowgroup", "table", "treegrid"),
    "rowheader" -> Set("row"),
    "tab" -> Set("tablist"),
    "treeitem" -> Set("tree", "group")
  )

  /** Roles that are empty without at least one of these inside. */
  val requiredChildren: Map[String, Set[String]] = Map(
    "list" -> Set("listitem", "group"),
    "listbox" -> Set("option", "group"),
    "menu" -> Set("menuitem", "menuitemcheckbox", "menuitemradio", "group"),
    "menubar" -> Set("menuitem", "menuitemcheckbox", "menuitemradio", "group"),
    "radiogroup" -> Set("radio"),
    "table" -> Set("row", "rowgroup"),
    "tablist" -> Set("tab"),
    "tree" -> Set("treeitem", "group")
  )

  /** The autocomplete tokens the HTML specification defines. */
  val autocompleteTokens: Set[String] = Set(
    "on",
    "off",
    "name",
    "honorific-prefix",
    "given-name",
    "additional-name",
    "family-name",
    "honorific-suffix",
    "nickname",
    "username",
    "new-password",
    "current-password",
    "one-time-code",
    "organization-title",
    "organization",
    "street-address",
    "address-line1",
    "address-line2",
    "address-line3",
    "address-level4",
    "address-level3",
    "address-level2",
    "address-level1",
    "country",
    "country-name",
    "postal-code",
    "cc-name",
    "cc-given-name",
    "cc-additional-name",
    "cc-family-name",
    "cc-number",
    "cc-exp",
    "cc-exp-month",
    "cc-exp-year",
    "cc-csc",
    "cc-type",
    "transaction-currency",
    "transaction-amount",
    "language",
    "bday",
    "bday-day",
    "bday-month",
    "bday-year",
    "sex",
    "url",
    "photo",
    "tel",
    "tel-country-code",
    "tel-national",
    "tel-area-code",
    "tel-local",
    "tel-extension",
    "email",
    "impp"
  )

  /** Prefixes a token may carry before the field name. */
  val autocompleteModifiers: Set[String] =
    Set("shipping", "billing", "home", "work", "mobile", "fax", "pager")

}
