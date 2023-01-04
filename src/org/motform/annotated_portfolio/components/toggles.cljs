(ns org.motform.annotated-portfolio.components.toggles
  (:require [re-frame.core :as rf]
            [org.motform.annotated-portfolio.components.input :as input]))

(defn Toggles []
  (when @(rf/subscribe [:radial/complete?])
    [:section#toggles.sidebar-buttons.row.spaced.pad-half
     [input/Toggle "play"
      {:active-icon   :pause
       :inactive-icon :play
       :active-when [:radial/playing?]
       :event [:radial/play]}]]))

