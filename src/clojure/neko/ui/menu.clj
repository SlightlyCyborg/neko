; Copyright © 2013 Alexander Yakushev.
; All rights reserved.
;
; This program and the accompanying materials are made available under the
; terms of the Eclipse Public License v1.0 which accompanies this distribution,
; and is available at <http://www.eclipse.org/legal/epl-v10.html>.
;
; By using this software in any fashion, you are agreeing to be bound by the
; terms of this license.  You must not remove this notice, or any other, from
; this software.

(ns neko.ui.menu
  "Provides utilities for declarative options menu generation.
  Intended to replace XML-based menu layouts."
  (:require [neko.ui :as ui])
  (:use [neko.ui.mapping :only [defelement]]
        [neko.ui.traits :only [deftrait]])
  (:import [android.view Menu MenuItem]
           android.view.View))

(defn make-menu
  "Inflates the given MenuBuilder instance with the declared menu item
  tree. Root of the tree is a sequence that contains element
  definitions (see doc for `neko.ui/make-ui` for element definition
  syntax). Elements supported are `:item`, `:group` and `:menu`.

  `:item` is a default menu element. See supported traits for :item for
  more information.

  `:group` allows to unite items into a single category in order to
  later operate on the whole category at once.

  `:menu` element creates a submenu that can in its own turn contain
  other `:item` and `:group` elements. Only one level of submenus is
  supported. Note that :menu creates an item for itself and can use
  all the attributes that apply to items. "
  ([menu tree]
     (make-menu menu Menu/NONE tree))
  ([menu group tree]
     (doseq [[element-kw attributes & subelements] tree]
       (let [id (.hashCode (or (:id attributes) Menu/NONE))
             order (.hashCode (or (:order attributes) Menu/NONE))]
         (case element-kw
           :group
           (make-menu menu id subelements)

           :item
           (ui/apply-attributes
            :item
            (.add ^Menu menu ^int group ^int id ^int order "")
            (dissoc attributes :id :order) {})

           :menu
           (let [submenu (.addSubMenu ^Menu menu ^int group
                                      ^int id ^int order "")]
             (ui/apply-attributes :item (.getItem submenu)
                                  (dissoc attributes :id :order) {})
             (make-menu submenu subelements)))))))

;; ## Element definitions and traits

(defelement :item
  :inherits nil
  :traits [:show-as-action :on-menu-item-click :action-view])

;; ### ShowAsAction attribute

(defn show-as-action-value
  "Returns an integer value for the given keyword, or the value itself."
  [value]
  (if (keyword? value)
    (case value
      :always               MenuItem/SHOW_AS_ACTION_ALWAYS
      :collapse-action-view MenuItem/SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW
      :if-room              MenuItem/SHOW_AS_ACTION_IF_ROOM
      :never                MenuItem/SHOW_AS_ACTION_NEVER
      :with-text            MenuItem/SHOW_AS_ACTION_WITH_TEXT)
    value))

(deftrait :show-as-action
  "Takes :show-as-action attribute, which could be an integer value or
  one of the following keywords: :always, :collapse-action-view,
  :if-room, :never, :with-text; or a vector with these values, to
  which bit-or operation will be applied."
  [^MenuItem wdg, {:keys [show-as-action]} _]
  (let [value (if (vector? show-as-action)
                (apply bit-or (map show-as-action-value show-as-action))
                (show-as-action-value show-as-action))]
    (.setShowAsAction wdg value)))

;; ### OnMenuItemClick attribute

(defn on-menu-item-click-call
  "Takes a function and yields a MenuItem.OnMenuItemClickListener
  object that will invoke the function. This function must take one
  argument, an item that was clicked."
  [handler-fn]
  (reify android.view.MenuItem$OnMenuItemClickListener
    (onMenuItemClick [this item]
      (handler-fn item)
      true)))

(defmacro on-menu-item-click
  "Takes a body of expressions and yields a
  MenuItem.OnMenuItemClickListener object that will invoke the body.
  The body takes an implicit argument 'item' that is the item that was
  clicked."
  [& body]
  `(on-menu-item-click-call (fn [~'item] ~@body)))

(deftrait :on-menu-item-click
  "Takes :on-click attribute, which should be function of one
  argument, and sets it as an OnClickListener for the widget."
  #(:on-click %)
  [^MenuItem wdg, {:keys [on-click]} _]
  (.setOnMenuItemClickListener wdg (on-menu-item-click-call on-click))
  {:attributes-fn #(dissoc % :on-click)})

;; ### ActionView attribute

(deftrait :action-view
  "Takes `:action-view` attribute which should either be a View
  instance or a UI definition tree, and sets it as an action view for
  the menu item. For UI tree syntax see docs for `neko.ui/make-ui`.
  Custom context can be used for UI inflation by providing `:context`
  attribute."
  [^MenuItem wdg, {:keys [action-view context]} _]
  (let [view (cond (instance? View action-view) action-view
                   context (ui/make-ui context action-view)
                   :else (ui/make-ui action-view))]
    (.setActionView wdg ^View view))
  {:attributes-fn #(dissoc % :action-view :context)})
