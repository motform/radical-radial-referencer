(ns org.motform.annotated-portfolio.components.radial
  (:require [clojure.string :as str]
            [clojure.math :as math]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [org.motform.annotated-portfolio.components.input :as input]
            [org.motform.annotated-portfolio.components.tags :as tags]
            [org.motform.annotated-portfolio.components.utils :as utils]))

(defn Project [{:reference/keys [images]}]
  (let [image-URL (-> @(rf/subscribe [:images/for-reference images]) first :image/data-URL)
        editing? (= :project @(rf/subscribe [:reference/editing]))]
    [:div#project.item
     {:style {:background-image (utils/CSS-URL image-URL)
              :border-color (when (or editing? @(rf/subscribe [:radial/playing?])) "var(--highlight)")}
      :on-pointer-down #(do (rf/dispatch [:reference/editing :project])
                            (rf/dispatch [:project/edit :project/clicked? true]))}]))

(defn Reference-Name [{:reference/keys [name id tags]}]
  (r/with-let [*state (r/atom {:editing? false
                               :initial-value nil
                               :this nil})]
    (let [name? (not (str/blank? name))
          inactive? @(rf/subscribe [:tags/inactive? tags])]
      [:div.row.centered
       {:on-pointer-down #(swap! *state assoc :editing? true :initial-value name)}
       (if (:editing? @*state)
         [:input#reference-name-input
          {:type :text
           :auto-focus true
           :value name
           :ref #(swap! *state assoc :this %)
           :on-blur #(swap! *state assoc :editing? false)
           :on-change #(rf/dispatch [:reference/edit id :reference/name (input/value %)])
           :on-key-down #(case (.-key %)
                           "Enter" (.blur (:this @*state))
                           "Escape" (do (.blur (:this @*state))
                                        (rf/dispatch [:reference/edit id :reference/name (:initial-value @*state)]))
                           nil)}]
         [:label.reference-name.pad-quarter
          {:class (cond (not name?) "reference-name-placeholder"
                        inactive?   "reference-name-inactive")}
          (if-not name? "new reference" name)])])))

(defn Reference-Image [{:reference/keys [images active-image id tags]}]
  (let [editing?  (= id (:reference/id @(rf/subscribe [:reference/editing])))
        inactive? @(rf/subscribe [:tags/inactive? tags])
        highlit?  @(rf/subscribe [:tags/highlit? tags])
        image-URL (->> @(rf/subscribe [:images/for-reference images])
                       (some #(when (= active-image (% :image/id)) %))
                       :image/data-URL)]
    [:img.reference-image
     {:class (cond-> []
               (or highlit? editing?) (concat ["reference-highlight" "shadow-large"])
               inactive? (conj "reference-inactive")
               (not image-URL) (conj "reference-image-placeholder"))
      :src image-URL
      :on-pointer-down #(rf/dispatch [:reference/editing id])}]))

(defn Reference [{:reference/keys [tags index] :as reference} radian reference-scale]
  (let [degress-per-reference (/ 360 @(rf/subscribe [:references/count]))
        degress  (* degress-per-reference index)
        rotation (+ degress 90 @(rf/subscribe [:radial/delta]))]
    [:div.reference.column.spaced.gap-eight
     {:style {:transform (str "rotate(" rotation "deg)"
                              "translate(-" radian "px)"
                              "rotate(-" rotation "deg)")
              :width (str reference-scale "%")}}
     [Reference-Image reference]
     [:div.reference-meta.row.spaced
      [Reference-Name reference]
      [tags/Reference tags]]]))

(defn reference-scale [references]
  (case (count references) ; case goes burrr
    (1 2 3 4 5) 14
    6 12
    7 10
    8 9
    9 8
    (10 11) 7
    12 6
    13 5
    4))

(defn handle-radial-drag [*state old-delta playing?]
  (fn [event]
    (when (and playing? (:pointer-down? @*state))
      (let [x       (- (.-pageX event) (/ (.. js/document -body -offsetWidth)  2))
            y (* -1 (- (.-pageY event) (/ (.. js/document -body -offsetHeight) 2)))
            theta   (* (math/atan2 y x) (/ 180 math/PI))
            degrees (- 90 theta)
            old-dragged (:dragged @*state)
            delta' (mod (- (+ old-delta degrees) old-dragged) 360)
            delta  (if (> delta' 0) (+ delta' 360) delta')]
        (swap! *state assoc :degrees degrees)
        (rf/dispatch [:radial/delta delta])))))

(defn Radial []
  (r/with-let [*radial (r/atom nil)
               *state (r/atom {:pointer-down? false
                               :degrees nil})
               delta @(rf/subscribe [:radial/delta])]
    (let [radian (/ (when @*radial (.-offsetWidth @*radial)) 2)
          project @(rf/subscribe [:db/project])
          references (-> @(rf/subscribe [:db/references]) vals utils/enumerate)
          playing? @(rf/subscribe [:radial/playing?])]
      [:div#radial-container.column.centered>section#radial
       {:class [(when playing? "radial-background")
                (when (:pointer-down? @*state) "radial-dragging")]
        :ref #(reset! *radial %)
        :on-pointer-down #(swap! *state assoc :pointer-down? true)
        :on-pointer-move (handle-radial-drag *state delta playing?)
        :on-pointer-up   #(swap! *state assoc :pointer-down? false :degress 0)}

       [Project project]

       (for [[i reference] references]
         ^{:key (:reference/id reference)}
         [Reference (assoc reference :reference/index i) radian (reference-scale references)])])))

(defonce do-timer (js/setInterval #(rf/dispatch [:radial/tick]) 10)) ; TODO: move into a fx that sets and stops the interval
