(ns org.motform.annotated-portfolio.components.sidebar
  (:require [re-frame.core :as rf]
            [org.motform.annotated-portfolio.components.input :as input]
            [org.motform.annotated-portfolio.components.utils :as utils]))

(defn Header []
  [:header.sidebar-buttons.row.spaced
   [input/Button "reference"
    {:event [:reference/new]
     :icon  :plus
     :label "Add Reference"}]
   [:div.row
    [input/Button "undo"
     {:event [:undo]
      :icon  :undo
      :enabled-when [:undos?]}]
    [input/Button "redo"
     {:event [:redo]
      :icon  :redo
      :enabled-when [:redos?]}]
    [input/Button "heartbreak"
     {:event [:state/reset!]
      :icon  :heartbreak}]]])

;; NOTE This should really be named `Reference-Editor`, should it not?
(defn Property-Editor [{:reference/keys [name id weight] :as reference}]
  [:section#property-editor.column.gap-half
   [input/Text
    {:label "name"
     :on-change #(rf/dispatch [:reference/edit id :reference/name (input/value %)])
     :placeholder "new referece"
     :value name}]
   [:hr]
   [input/Tags reference]
   [:hr]
   [input/Images reference
    {:label "image"
     :on-change :reference/image-upload}]
   [input/Button "delete"
    {:text "Delete"
     :label "Delete reference"
     :type :destructive
     :event [:reference/delete reference]}]])

(defn Project-Editor []
  (let [{:reference/keys [name] :as project} @(rf/subscribe [:db/project])]
    [:section#project-editor.column.gap-half
     [input/Text
      {:label "project name"
       :value name
       :on-change #(rf/dispatch [:project/edit :reference/name (input/value %)])
       :placeholder "new project…"}]
     [:hr]
     [input/Images project
      {:label "project image"
       :on-change :project/image-upload}]]))

(defn Footer []
  [:footer.sidebar-buttons.row.spaced
   [input/Button "screenshot"
    {:event [:screenshot/take]
     :icon  :camera
     :label "Screenshot"
     :enabled-when [:radial/complete?]}]
   [:div.row
    [input/Button "export"
     {:event [:state/export]
      :icon  :export
      :label "Export"
      :enabled-when [:radial/complete?]}]
    [input/File-Upload "import"
     {:event [:state/upload]
      :icon  :import
      :label "Import"}]]])

(defn Undos []
  [:section.column
   (for [[i undo] (utils/enumerate @(rf/subscribe [:undo-explanations]))]
     ^{:key i}
     [:div.pad-eight (str i " " undo)])])

(defn Tip-Editor []
  [:section#tip-editor.pad-half
   "select a reference edit tags and properties"])

(defn Sidebar []
  (let [editing @(rf/subscribe [:reference/editing])]
    [:aside#sidebar.column
     [Header]
     [:hr]
     (cond (= :project editing) [Project-Editor]
           editing [Property-Editor editing]
           :else [Tip-Editor])
     [Footer]]))
