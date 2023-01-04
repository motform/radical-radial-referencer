(ns org.motform.annotated-portfolio.components.input
  "Widgets take an event-vector under `:event`, to which they conj .-target .-value"
  (:require [re-frame.core :as rf]
            [reagent.core :as r]
            [org.motform.annotated-portfolio.components.tags :as tags]
            [org.motform.annotated-portfolio.components.utils :as utils]))

(defn value [element]
  (.. element -target -value))

(defn input-event [ev]
  (fn [event]
    (rf/dispatch (conj ev (value event)))))

(defn Text [{:keys [label value on-change placeholder auto-focus]}]
  [:div#sidebar-input-name.sidebar-input.column.gap-quarter
   [:label label]
   [:input
    {:type :text
     :value value
     :placeholder placeholder
     :auto-focus auto-focus
     :on-change on-change}]])

(defn- handle-file-upload [*state on-change id event]
  (let [files (array-seq (.. event -target -files))]
    (cond (not-every? utils/image-file? files) (swap! *state assoc :error :error/file-type)
          (some (utils/over-file-size? 1500000) files) (swap! *state assoc :error :error/file-size)
          :else (rf/dispatch [on-change id files]))))

(defn Image [{:image/keys [id data-URL]} reference]
  [:img.image-picker-thumbnail
   {:class (when (= (:reference/active-image reference) id) "image-highlight")
    :on-pointer-down #(rf/dispatch [:reference/edit (:reference/id reference) :reference/active-image id])
    :src data-URL}])

(defn Images [{:reference/keys [id images] :as reference} {:keys [label on-change]}]
  (r/with-let [*state (r/atom {:error nil})]
    (let [images @(rf/subscribe [:images/for-reference images])
          multiple-images? (> (count images) 1)]
      [:div#sidebar-image-name.sidebar-input.column.gap-quarter
       [:label (utils/pluralize label multiple-images?)]
       [:input {:type :file
                :multiple true
                :on-change (partial handle-file-upload *state on-change id)}]

       (case (:error @*state)
         :error/file-type [:p.error.pad-quarter "Invalid file type, rrr can only handle image files."]
         :error/file-size [:p.error.pad-quarter
                           "The image is too large, max file size is ~1MB."
                           "Try compressing the image with " [:a {:href "https://squoosh.app"} "squoosh."]]
         nil)

       [:div.row.image-picker.gap-eight
        (when multiple-images?
          (for [image images]
            ^{:key (:image/id image)}
            [Image image reference]))]])))

(defn File [*state {:keys [label key]}]
  [:<>
   [:label label]
   [:input {:type :file
            :style {:height "26px"}
            :multiple true
            :on-change #(swap! *state assoc key (array-seq (value %)))}]])

(defn Button [id {:keys [type enabled-when event icon label]}]
  (let [enabled? (if enabled-when @(rf/subscribe enabled-when) true)]
    [:button.sidebar-button.row.gap-quarter.centered
     {:id (str id "-button")
      :class (str (when label "sidebar-button-labeled ")
                  (when (= type :destructive) "button-destructive"))
      :disabled (not enabled?)
      :on-pointer-down #(when enabled? (rf/dispatch event))}
     (utils/icons icon)
     (when label [:label label])]))

(defn Tags [reference]
  [:div#sidebar-tag-name.sidebar-input.column.gap-quarter
   [:label "Tags"]
   [:section.tag-input
    [:ul.column
     (for [tag (vals @(rf/subscribe [:db/tags]))]
       ^{:key (:tag/id tag)}
       [tags/Tag-Toggle reference tag])]]
   [tags/Input-Form reference]])

(defn Toggle [id {:keys [event active-icon inactive-icon active-when]}]
  (let [active? @(rf/subscribe active-when)]
    [:button.toggle.pad-quarter.row.centered
     {:id (str id "-toggle")
      :class (when active? "toggle-active")
      :on-pointer-down (input-event event)}
     (utils/icons (if active? active-icon inactive-icon))]))

(defn Slider [id {:keys [event min max step label value]}]
  [:div#sidebar-input-name.sidebar-input.column.gap-quarter
   [:label label]
   [:input
    {:type :range
     :id (str id "-slider")
     :max max
     :min min
     :on-change (input-event event)
     :step step
     :value value}]])

(defn File-Upload [id {:keys [label icon enabled-when event]}]
  (let [enabled? (if enabled-when @(rf/subscribe enabled-when) true)]
    [:<>
     [:input {:type :file
              :id "hidden-file-input"
              :on-change  #(rf/dispatch (conj event (-> % .-target .-files array-seq first)))
              :style {:display "none"}}]
     [:button.sidebar-button.row.gap-quarter.centered
      {:id (str id "-file")
       :class (when label "sidebar-button-labeled ")
       :disabled (not enabled?)
       :on-pointer-down #(.click (.querySelector js/document "#hidden-file-input"))}
      (utils/icons icon)
      (when label [:label label])]]))

