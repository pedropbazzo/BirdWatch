(ns birdwatch.ui.search)

(defn- search-view [app put-fn]
  [:form.pure-form
   [:fieldset
    [:input {:type "text" :value (:search-text @app)
             :on-key-press #(when (== (.-keyCode %) 13) (put-fn [:start-search]))
             :on-change #(put-fn [:set-search-text (.. % -target -value)])
             :placeholder "Example search: java (job OR jobs OR hiring)"}]
    [:button.pure-button.pure-button-primary {:on-click #(put-fn [:start-search])}
     [:span {:class "glyphicon glyphicon-search"}]]]])
