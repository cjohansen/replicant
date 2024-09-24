(ns replicant.test-test
  (:require [replicant.test :as sut]
            [clojure.test :refer [deftest is testing]]))

(def dictionaries
  {:en {:click "Click"
        :no-click "Don't click"}})

(defn i18n [locale _ [k]]
  (get-in dictionaries [locale k]))

(defn button [attrs [text-k]]
  [:button.btn (select-keys attrs [:class])
   [:ui/i18n text-k]])

(defn panel [{:keys [buttons class]} & _]
  [:div.panel (cond-> {}
                class (assoc :class class))
   (for [button buttons]
     [(if (:primary? button)
        :ui/button.btn-primary
        :ui/button)
      (:text-k button)])])

(def aliases
  {:ui/button #'button
   :ui/panel #'panel
   :ui/i18n #(apply i18n :en %&)})

(deftest aliasexpand-1-test
  (testing "Expands first level of aliases"
    (is (= (-> [:ui/panel
                {:buttons
                 [{:text-k :click :primary? true}
                  {:text-k :no-click}]}]
               (sut/aliasexpand-1 {:aliases aliases}))
           [:div {:class #{"panel"}}
            [:ui/button.btn-primary :click]
            [:ui/button :no-click]])))

  (testing "Expands two levels of aliases"
    (is (= (-> [:ui/panel
                {:buttons
                 [{:text-k :click :primary? true}
                  {:text-k :no-click}]}]
               (sut/aliasexpand-1 {:aliases aliases})
               (sut/aliasexpand-1 {:aliases aliases}))
           [:div {:class #{"panel"}}
            [:button {:class #{"btn" "btn-primary"}} [:ui/i18n :click]]
            [:button {:class #{"btn"}} [:ui/i18n :no-click]]]))))

(deftest aliasexpand-test
  (testing "Expands all aliases"
    (is (= (-> [:ui/panel
                {:buttons
                 [{:text-k :click :primary? true}
                  {:text-k :no-click}]}]
               (sut/aliasexpand {:aliases aliases}))
           [:div {:class #{"panel"}}
            [:button {:class #{"btn" "btn-primary"}} "Click"]
            [:button {:class #{"btn"}} "Don't click"]]))))
