(ns scicloj.scittles.v1.table
  "Interactive table visualisations."
  (:require [hiccup.core :as hiccup]
            [hiccup.element :as elem]
            [jsonista.core :as jsonista])
  (:import java.io.File
           java.util.UUID))

(defn row-vectors->table-hiccup [column-names row-vectors]
  [:table {:class :table}
   [:thead
    (->> column-names
         (mapv (fn [x] [:th x]))
         (into [:tr]))]
   (->> row-vectors
        (map-indexed
         (fn [i row]
           (->> row
                (mapv (fn [x] [:td (-> x
                                       println
                                       with-out-str)]))
                (into [:tr]))))
        vec
        (into [:tbody]))])

(defn row-maps->table-hiccup
  ([row-maps]
   (-> row-maps
       first
       keys
       (row-maps->table-hiccup row-maps)))
  ([column-names row-maps]
   (if column-names
     (->> row-maps
          (mapv (fn [row] ; Actually row can be a record, too.
                  (mapv #(get row %) column-names)))
          (row-vectors->table-hiccup column-names))
     ;; else
     (row-maps->table-hiccup row-maps))))

(defn ->table-hiccup [{:keys [row-maps row-vectors column-names]}]
  (assert column-names)
  (if row-vectors
    (row-vectors->table-hiccup column-names row-vectors)
    (do
      (assert row-maps)
      (row-maps->table-hiccup column-names row-maps))))

(def datatables-default-options
  {:sPaginationType "full_numbers"})

(defn datatables-js [datatables-options table-id]
  (->> (str "$(function() {
               $(\"#" table-id "\").DataTable("
            (jsonista/write-value-as-string
             (merge datatables-default-options
                    datatables-options))
            ");"
            "});")))

(def datatables-css
  "
.even {
}

.odd {
}")

(defn table-hiccup->table-html
  ([table-hiccup]
   (table-hiccup->table-html "" table-hiccup))
  ([id table-hiccup]
   (hiccup/html
    [:head
     [:style datatables-css]]
    ;; Take a :table element with specific id and class:
    [:body
     [:table.stripe.dataTable {:id id}
      ;; Now take the givn table-hiccup without its :table prefix - we have already got one.
      (rest table-hiccup)]])))

(defn table-hiccup->datatables-html
  ([table-hiccup] (table-hiccup->datatables-html table-hiccup {}))
  ([table-hiccup datatables-options]
   (let [id (str (UUID/randomUUID))]
     (->> (str (table-hiccup->table-html id table-hiccup)
               (->> (datatables-js datatables-options
                                   id)
                    elem/javascript-tag
                    hiccup/html))))))


(def row-maps->table-html
  (comp table-hiccup->table-html
        row-maps->table-hiccup))

(def row-maps->datatables-html
  (comp table-hiccup->datatables-html
        row-maps->table-hiccup))
