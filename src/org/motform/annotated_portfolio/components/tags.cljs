(ns org.motform.annotated-portfolio.components.tags
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [org.motform.annotated-portfolio.components.utils :as utils]))

(defn Reference [tags]
  (let [inactive? @(rf/subscribe [:tags/inactive? tags])]
    [:ul.row.pad-eight
     (doall ; this is a lazy seq for some reason, probably becuase of my sub, so we have to wrap it in a `doall` to force reactive evaluation
      (for [{:tag/keys [id color]} @(rf/subscribe [:tags/for-reference tags])]
        ^{:key id}
        [:li.row.gap-eight.pad-eight
         {:class (cond @(rf/subscribe [:tag/highlit? id]) "tag-highlight"
                       inactive? "tag-inactive"
                       @(rf/subscribe [:tag/inactive? id]) "tag-inactive")
          :on-pointer-down #(rf/dispatch [:tags.active/toggle id])
          :on-pointer-over #(rf/dispatch [:tags/highlight id])
          :on-pointer-out  #(rf/dispatch [:tags/remove-highlight id])}
         [:div.tag-color {:style {:background-color color}}]]))]))

(defn Tag [{:tag/keys [id color name]}]
  [:li.tag-toggle.row.gap-eight.pad-eight
   {:class (when @(rf/subscribe [:tag/inactive? id]) "tag-inactive")
    :on-pointer-down #(rf/dispatch [:tags.active/toggle id])
    :on-pointer-over #(rf/dispatch [:tags/highlight id])
    :on-pointer-out  #(rf/dispatch [:tags/remove-highlight id])}
   [:div.tag-color {:style {:background-color color}
                    :class (when @(rf/subscribe [:tag/highlit? id]) "tag-highlight")}]
   [:p name]])

(defn Legend []
  (let [tags @(rf/subscribe [:tags/in-use])]
    (when (seq tags)
      [:div.tag-legend-target
       [:div.row-reverse>div#tag-clear.row.pad-eight
        {:on-pointer-down #(rf/dispatch [:tags.active/clear])}
        [:p "Clear selection"]]
       [:aside#tag-legend.column
        [:ul.column
         (for [tag tags]
           ^{:key (:tag/id tag)}
           [Tag tag])]]])))

(defn Tag-Toggle [reference {:tag/keys [id color name]}]
  (let [active? (contains? (:reference/tags reference) id)]
    [:li.tag-toggle.row.gap-eight.spaced.pad-eight
     {:on-pointer-down #(rf/dispatch [:reference/toggle-tag (:reference/id reference) id])
      :class (when (not active?) "tag-toggle-inactive")}
     [:div.row.centered.gap-quarter {:style {:width "100%"}}
      [:div.tag-color
       {:style {:background-color color}}]
      [:p.tag-name name]
      [:div.tag-delete
       {:on-pointer-down #(do (.stopPropagation %)
                              (rf/dispatch [:tag/delete id]))}
       (utils/icons :x)]]]))

(defn tag-template []
  #:tag{:id (random-uuid) :color "#E6423A" :name ""})

(defn submit-tag [*new-tag reference]
  (rf/dispatch [:tag/add @*new-tag])
  (rf/dispatch [:reference/toggle-tag (:reference/id reference) (:tag/id @*new-tag)])
  (reset! *new-tag (tag-template)))

(defn Input-Form [reference]
  (r/with-let [*new-tag (r/atom (tag-template))]
    (let [complete? (not (str/blank? (:tag/name @*new-tag)))]
      [:section.tag-input-form.row.gap-quarter.centered
       [:input
        {:type "color"
         :value (:tag/color @*new-tag)
         :on-change #(swap! *new-tag assoc :tag/color (.. % -target -value))}]
       [:input.tag-name-input
        {:type "text"
         :value (:tag/name @*new-tag)
         :placeholder "add tag"
         :on-change #(swap! *new-tag assoc :tag/name (.. % -target -value))
         :on-key-down #(case (.. % -key)
                         "Enter" (when complete? (submit-tag *new-tag reference))
                         nil)}]
       [:button.tag-input-submit
        {:disabled (not complete?)
         :on-pointer-down #(when complete? (submit-tag *new-tag reference))}
        (utils/icons :plus)]])))
