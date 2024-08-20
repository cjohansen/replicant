(ns replicant.string-test
  (:require [replicant.string :as sut]
            [clojure.test :refer [deftest is testing]]))

(deftest string-render-test
  (testing "Renders string"
    (is (= (sut/render "Hello world") "Hello world")))

  (testing "Renders simple element"
    (is (= (sut/render [:h1 "Hello world"]) "<h1>Hello world</h1>")))

  (testing "Renders id from hiccup tag"
    (is (= (sut/render [:h1#heading "Hello world"])
           "<h1 id=\"heading\">Hello world</h1>")))

  (testing "Renders id"
    (is (= (sut/render [:h1 {:id "headz"} "Hello world"])
           "<h1 id=\"headz\">Hello world</h1>")))

  (testing "Renders classes"
    (is (= (sut/render [:h1.text-sm {:class ["border-4"]} "Hello"])
           "<h1 class=\"border-4 text-sm\">Hello</h1>")))

  (testing "Renders styles from maps"
    (is (= (sut/render [:h1 {:style {:border "1px solid red"
                                     :color "red"}} "Red"])
           "<h1 style=\"border: 1px solid red; color: red;\">Red</h1>")))

  (testing "Renders styles from strings"
    (is (= (sut/render [:h1 {:style "border: 1px solid red; color: yellow"} "Red"])
           "<h1 style=\"border: 1px solid red; color: yellow;\">Red</h1>")))

  (testing "Renders arbitrary attributes"
    (is (= (sut/render [:h1 {:title "Color"} "Red"])
           "<h1 title=\"Color\">Red</h1>")))

  (testing "Renders nested hiccup"
    (is (= (sut/render
            [:div
             [:h1.heading "Hello world"]
             [:p "Some text"]])
           "<div><h1 class=\"heading\">Hello world</h1><p>Some text</p></div>")))

  (testing "Renders formatted markup"
    (is (= (sut/render
            [:div
             [:h1.heading "Hello world"]
             [:p "Some text"]]
            {:indent 2})
           (str "<div>\n"
                "  <h1 class=\"heading\">\n"
                "    Hello world\n"
                "  </h1>\n"
                "  <p>\n"
                "    Some text\n"
                "  </p>\n"
                "</div>\n"))))

  (testing "Renders SVG node"
    (is (= (sut/render
            [:svg {:viewBox "0 0 100 100"}
             [:g [:use {:xlink:href "#icon"}]]])
           (str "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 100 100\">"
                "<g>"
                "<use xlink:href=\"#icon\"></use>"
                "</g>"
                "</svg>")))))
