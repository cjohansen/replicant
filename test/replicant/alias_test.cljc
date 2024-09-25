(ns replicant.alias-test
  (:require [replicant.alias :as sut]
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

(defn list [_ items]
  [:ul.list items])

(def aliases
  {:ui/button #'button
   :ui/panel #'panel
   :ui/i18n #(apply i18n :en %&)
   :ui/list #'list})

(deftest expand-1-test
  (testing "Expands first level of aliases"
    (is (= (-> [:ui/panel
                {:buttons
                 [{:text-k :click :primary? true}
                  {:text-k :no-click}]}]
               (sut/expand-1 {:aliases aliases}))
           [:div {:class #{"panel"}}
            [:ui/button.btn-primary :click]
            [:ui/button :no-click]])))

  (testing "Expands two levels of aliases"
    (is (= (-> [:ui/panel
                {:buttons
                 [{:text-k :click :primary? true}
                  {:text-k :no-click}]}]
               (sut/expand-1 {:aliases aliases})
               (sut/expand-1 {:aliases aliases}))
           [:div {:class #{"panel"}}
            [:button {:class #{"btn" "btn-primary"}} [:ui/i18n :click]]
            [:button {:class #{"btn"}} [:ui/i18n :no-click]]])))

  (testing "Passes children to alias fn as a seq and flattens expanded hiccup"
    (is (= (-> [:ui/list
                [:li "One thing"]
                [:li "Another thing"]]
               (sut/expand-1 {:aliases aliases}))
           [:ul {:class #{"list"}}
            [:li "One thing"]
            [:li "Another thing"]]))))

(deftest expand-test
  (testing "Expands all aliases"
    (is (= (-> [:ui/panel
                {:buttons
                 [{:text-k :click :primary? true}
                  {:text-k :no-click}]}]
               (sut/expand {:aliases aliases}))
           [:div {:class #{"panel"}}
            [:button {:class #{"btn" "btn-primary"}} "Click"]
            [:button {:class #{"btn"}} "Don't click"]]))))
