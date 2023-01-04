(ns org.motform.annotated-portfolio.main
  (:require [re-frame.core :as rf]
            [reagent.core :as r]
            [reagent.dom :as rdom]
            [org.motform.annotated-portfolio.events]
            [org.motform.annotated-portfolio.subs]
            [org.motform.annotated-portfolio.db :as db]
            [org.motform.annotated-portfolio.keyboard-shortcuts :as keyboard-shortcuts]
            [org.motform.annotated-portfolio.components.landing :as landing]
            [org.motform.annotated-portfolio.components.radial :as radial]
            [org.motform.annotated-portfolio.components.sidebar :as sidebar]
            [org.motform.annotated-portfolio.components.toggles :as toggles]
            [org.motform.annotated-portfolio.components.tags :as tags]))

(enable-console-print!)

(def platform
  (let [platform-string (.. js/window -navigator -platform)]
    (cond (re-find #"iPhone|iPad|Mac" platform-string) :platform/mac
          (re-find #"Win|Linux" platform-string) :platform/pc)))

(defn app []
  (r/with-let [_ (.addEventListener js/document "keydown" (keyboard-shortcuts/global platform))]
    [:<>
     [landing/Tutorial]
     [:div.row {:style {:height "100%"}}
      [toggles/Toggles]
      [sidebar/Sidebar]
      [:main#screenshot
       {:on-pointer-down #(when (keyboard-shortcuts/non-reference-target? %)
                            (rf/dispatch [:reference/editing nil]))}
       [radial/Radial]
       [tags/Legend]]]]))

(defn render []
  (rdom/render [app] (.getElementById js/document "mount")))

(defn ^:dev/after-load clear-cache-and-render! []
  (rf/clear-subscription-cache!)
  (render))

(defn ^:export mount []
  (rf/dispatch-sync [:db/initialize db/default])
  (rf/dispatch [:radial/exported?])
  (render))
