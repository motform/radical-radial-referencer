(ns org.motform.annotated-portfolio.components.header
  (:require [clojure.string :as str]
            [org.motform.annotated-portfolio.components.utils :as utils]
            [re-frame.core :as rf]))

(defn Button [id {:keys [event icon enabled-when]}]
  (let [tooltip-id (str id "-tooltip")
        tooltip (str/capitalize id)
        enabled? (if enabled-when @(rf/subscribe enabled-when) true)]
    [:button.header-util-button
     {:id (str id "-button")
      "aria-labelledby" tooltip-id
      :on-pointer-down #(when enabled? (rf/dispatch event))
      :disabled (not enabled?)}
     [:img {:src (utils/icons icon)}]
     [:div {:id tooltip-id
            :role "tooltip"}
      tooltip]]))


