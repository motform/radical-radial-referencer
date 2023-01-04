(ns org.motform.annotated-portfolio.components.landing
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [org.motform.annotated-portfolio.components.utils :as utils]))

(defn Focus [area]
  [:div#tutorial-focus {:class (str "tutorial-focus-" area)}])

(defn Tip-Add-Project []
  [:<>
   [:section#landing.column.gap-full
    [:h1 "radical radial referencer.®"]]
   [:div.row.gap-full.floating
    {:style {:top "49%"}}
    [:div.column.gap-eight
     [:p "begin by adding your own project"]
     [:p "click on the white circle"]]
    (utils/icons :arrow-right-circle)]])

(defn Tip-Edit-Project []
  [:section#landing.column.gap-full
   {:style {:top "10%"}}
   [:div.row.gap-half
    (utils/icons :arrow-left-circle)
    [:div.column.gap-eight
     [:p "to start, add some information about your project"]
     [:p "give it a name and an image"]]]])

(defn Tip-Edit-Reference []
  [:section#landing.column.gap-half
   {:style {:top "10%"}}
   [:div.row.gap-half
    (utils/icons :arrow-left-circle)
    [:div.column.gap-eight
     [:p "add some metadata to your reference"]
     [:p "we need a name, some tags and images"]]]])

(defn Tip-Add-Reference []
  [:section#landing.column.gap-half
   {:style {:top "20px"}}
   [:div.row.gap-half
    (utils/icons :arrow-left-circle)
    [:div.column.gap-eight
     [:p {:style {:margin-top "10px"}}
      "next, add a new reference"]]]])

(defn Tip-Export []
  [:div#tutorial-import.column.gap-quarter
   {:style {:top "92vh"}}
   [:p.row.gap-eight "once you feel ready," (utils/icons :export) [:span {:style {:color "var(--highlight)"}} "export"] "your work as a .radial file"]
   [:p.row.gap-eight "and take a" (utils/icons :camera) [:span {:style {:color "var(--highlight)"}} "screenshot"] "for the pictorial"]])

(defn Tip-Import []
  [:div#tutorial-import>p.row.gap-eight
   "already set things up?" (utils/icons :import) [:span {:style {:color "var(--highlight)"}} "import"] "your .radial file"])

(defn complete-project? [{:reference/keys [name images]}]
  (and (not (str/blank? name)) (seq images)))

(defn complete-reference? [{:reference/keys [name images tags]}]
  (and (not (str/blank? name)) (seq images) (seq tags)))

(defn Tutorial []
  (let [exported? @(rf/subscribe [:radial/exported?])
        project @(rf/subscribe [:db/project])
        reference (-> @(rf/subscribe [:db/references]) vals first)]
    (cond exported? nil


          (not (:project/clicked? project))
          [:<> [Tip-Add-Project] [Tip-Import]]

          (not (complete-project? project))
          [:<> [Focus "project"] [Tip-Edit-Project]]

          (not reference)
          [:<> [Focus "add-reference"] [Tip-Add-Reference]]

          (not (complete-reference? reference))
          [:<> [Focus "edit-reference"] [Tip-Edit-Reference]]

          (not exported?)
          [:<> [Focus "export"] [Tip-Export]]

          :else (js/console.error "Unknown state in components/Tutorial"))))
