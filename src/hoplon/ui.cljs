(ns hoplon.ui
  (:require
    [hoplon.core :as h]
    [clojure.string  :refer [join split]]
    [javelin.core    :refer [cell cell?]]
    [hoplon.ui.attrs :as a :refer [c r ratio? calc? points? ems? ->attr]]
    [hoplon.ui.elems :refer [box doc out mid in elem?]])
  (:require-macros
    [hoplon.ui    :refer [bind-in!]]
    [javelin.core :refer [cell= with-let]]))

;;; constants ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:dynamic *exceptions* nil)
(def ^:dynamic *scroll*     nil)

(def empty-icon-url  "data:;base64,iVBORw0KGgo=")
(def empty-image-url "data:image/gif;base64,R0lGODlhAQABAAAAACH5BAEKAAEALAAAAAABAAEAAAICTAEAOw==")

(def globals     [:initial :inherit])
(def adjusts     [:none])
(def haligns     [:left :right :center :justify])
(def valigns     [:top :middle :bottom :baseline :sub :super :text-top :text-bottom])
(def colors      [:transparent :antiquewhite :aqua :aquamarine :azure :beige
                  :bisque :black :blanchedalmond :blue :blueviolet :brown
                  :burlywood :cadetblue :chartreuse :chocolate :coral
                  :cornflowerblue :cornsilk :crimson :darkblue :darkcyan
                  :darkgoldenrod :darkgray :darkgreen :darkgrey :darkkhaki
                  :darkmagenta :darkolivegreen :darkorange :darkorchid :darkred
                  :darksalmon :darkseagreen :darkslateblue :darkslategray
                  :darkslategrey :darkturquoise :darkviolet :deeppink
                  :deepskyblue :dimgray :dimgrey :dodgerblue :firebrick
                  :floralwhite :forestgreen :fuchsia :gainsboro :ghostwhite
                  :gold :goldenrod :gray :green :greenyellow :grey :honeydew
                  :hotpink :indianred :indigo :ivory :khaki :lavender
                  :lavenderblush :lawngreen :lemonchiffon :lightblue :lightcoral
                  :lightcyan :lightgoldenrodyellow :lightgray :lightgreen
                  :lightgrey :lightpink :lightsalmon :lightseagreen
                  :lightskyblue :lightslategray :lightslategrey :lightsteelblue
                  :lightyellow :lime :limegreen :linen :maroon :mediumaquamarine
                  :mediumblue :mediumorchid :mediumpurple :mediumseagreen
                  :mediumslateblue :mediumspringgreen :mediumturquoise
                  :mediumvioletred :midnightblue :mintcream :mistyrose :moccasin
                  :navajowhite :navy :oldlace :olive :olivedrab :orange :orangered
                  :orchid :palegoldenrod :palegreen :paleturquoise
                  :palevioletred :papayawhip :peachpuff :peru :pink :plum
                  :powderblue :purple :rebeccapurple :red :rosybrown :royalblue
                  :saddlebrown :salmon :sandybrown :seagreen :seashell :sienna
                  :silver :skyblue :slateblue :slategray :slategrey :snow
                  :springgreen :steelblue :tan :teal :thistle :tomato :turquoise
                  :violet :wheat :white :whitesmoke :yellow :yellowgreen])
(def cursors     [:alias :all-scroll :auto :cell :context-menu :col-resize :copy
                  :crosshair :default :e-resize :ew-resize :grab :grabbing :help
                  :move :n-resize :ne-resize :nesw-resize :ns-resize :nw-resize
                  :nwse-resize :no-drop :none :not-allowed :pointer :progress
                  :row-resize :s-resize :se-resize :sw-resize :text
                  :vertical-text :w-resize :wait :zoom-in :zoom-out])
(def decorations [:none :underline :overline :line-through])
(def families    [:serif :sans-serif :monospace :cursive :fantasy])
(def kernings    [:auto :normal :none])
(def lengths     [:auto])
(def renderings  [:auto :optimizeSpeed :optimizeLegibility :geometricPrecision]) ;; todo: dash instead of camelcase
(def sizes       [:xx-small :x-small :small :medium :large :x-large :xx-large :larger :smaller])
(def smoothings  [:none :antialiased :subpixel-antialiased])
(def spacings    [:normal])
(def stretches   [:ultra-condensed :extra-condensed :condensed :semi-condensed :normal :semi-expanded :expanded :extra-expanded :ultra-expanded])
(def styles      [:normal :italic :oblique])
(def syntheses   [:none :weight :style :weight-style])
(def boxes       [:border :fill :view])
(def origins     [:left :right :top :bottom :center])
(def txstyles    [:preserve-3d :flat])
(def overflows   [:visible :hidden :scroll :auto])
(def weights     [:normal :bold :bolder :lighter :100 :200 :300 :400 :500 :600 :700 :800 :900])

;;; utils ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn route->hash [[path & [qmap]]]
  "transforms a urlstate of the form [[\"foo\" \"bar\"] {:baz \"barf\"}]
   to hash string in the form \"foo/bar&baz=barf\""
  (let [pair (fn [[k v]] (str (name k) "=" (pr-str v)))
        pstr (when path (apply str "/" (interpose "/" (map name path))))
        qstr (when qmap (apply str "?" (interpose "&" (map pair qmap))))]
    (str "#" pstr qstr)))

(defn hash->route [hash]
  "transforms a hash string to a urlstate of the form
   [[\"foo\" \"bar\"] {:baz \"barf\"}]"
  (let [[rstr qstr] (split (subs hash 2) #"\?")
        pair        #(let [[k v] (split % #"=")] [(keyword k) (cljs.reader/read-string v)])
        qmap        (->> (split qstr #"&") (map pair) (when (not-empty qstr)) (into {}))
        path        (->> (split rstr #"/") (remove empty?) (mapv keyword))]
    (vec (remove empty? [path qmap]))))

(def visibility->status
  "maps the visibility string to a status keyword"
  {"visible"   :foreground
   "hidden"    :background
   "prerender" :background
   "unloaded"  :terminated})

(defn throw-ui-exception [& msg]
  (when *exceptions*
    (swap! *exceptions* conj {:msg (apply str msg)})
    (throw (js/Error (apply str msg)))))

(defn vstr [vs]
  (join " " (map ->attr vs)))

(defn bind-cells [f] ;; todo: loop recur
  (fn [& vs]
    (let [watch (fn [i v] (if (cell? v) @(add-watch v i #(apply f (assoc (vec vs) i %4))) v))
          watch (fn [i v] (if (coll? v) (into (empty v) (map-indexed watch v)) (watch i v)))]
      (apply f (map-indexed watch vs)))))

(defn bind-with [f vs] ;;todo: consolidate with bind-cells
  (let [watch (fn [i v] (if (cell? v) @(add-watch v (gensym) #(f (assoc vs i %4))) v))]
    (f (map-indexed watch vs))))

(defn swap-elems! [e f & vs] ;; todo: factor out
  (cond (cell?   e) (cell= (apply swap-elems! e f vs))
        (vector? e) (doseq [e e] (apply swap-elems! e f vs)) ;;todo: handled with IElemValue if (hoplon.ui/elem?)
        (elem?   e) (apply f e vs)
        (string? e) identity
        (nil?    e) identity
        (fn?     e) identity
        :else       (throw-ui-exception "Invalid child of type " (type e) " with values " vs ".")))

(defn in? [v & kwvecs]  (some #{v} (apply conj kwvecs)))

(defn validate [validator]
  (fn [& vs]
    (doseq [v vs]
      (when-not (validator v)
        (throw-ui-exception "Error validating attribute value " v ".")))
    true))

(defn validate-cells [validator message] ;; todo: refactor to include attribute key
  (fn [& vs]
    (doseq [v vs :let [valid? (bind-cells validator)]]
      (when-not (valid? v)
        ; (prn :v v :valid? (valid? v) :validator (validator @v))
        (throw-ui-exception message " " v ".")))
    true))

;;; validation fns ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn adjust? [v]
  (cond (keyword? v) (in? v adjusts globals)
        (number?  v) (and (>= v 0) (<= v 1)) ;; todo: make a ratio
        (nil?     v) :initial
        :else        false))

(defn alignh? [v]
  (cond (keyword? v) (in? v haligns lengths globals)
        (number?  v) v
        (nil?     v) :initial
        :else        false))

(defn alignv? [v]
  (cond (keyword? v) (in? v valigns lengths globals)
        (number?  v) v
        (nil?     v) :initial
        :else        false))

(defn callback? [v]
  (cond (fn? v)   v
        (nil?     v) true
        :else        false))

(defn color? [v]
  (cond (keyword? v) (in? v colors globals)
        (a/color? v) v
        (nil?     v) :initial
        :else        false))

(defn cursor? [v]
  (cond (keyword? v) (in? v cursors globals)
        (nil?     v) :inital
        :else        false))

(defn dock? [v]
  (cond (keyword? v) (in? v lengths globals)
        (calc?    v) v
        (ratio?   v) v
        (ems?     v) v
        (points?  v) v
        (number?  v) v
        (true?    v) v
        (false?   v) true
        (nil?     v) :initial
        :else        false))

(defn family? [v]
  (cond (vector?  v) (every? identity v)
        (keyword? v) (in? v families globals)
        (string?  v) v
        (nil?     v) :initial
        :else        false))

(defn kerning? [v]
  (cond (keyword? v) (in? v kernings globals)
        (nil?     v) :initial
        :else        false))

(defn length? [v]
  (cond (keyword? v) (in? v lengths globals)
        (calc?    v) v
        (ratio?   v) v
        (ems?     v) v
        (points?  v) v
        (number?  v) v
        (nil?     v) :initial
        :else        false))

(defn opacity? [v]
  (cond (keyword? v) (in? v globals)
        (number?  v) (and (>= v 0) (<= v 1)) ;; todo: make a ratio
        (nil?     v) :initial
        :else        false))

(defn overflow? [v]
  (cond (keyword? v) (in? v overflows globals)
        (nil?     v) :initial
        :else        false))

(defn rendering? [v]
  (cond (keyword? v) (in? v renderings globals)
        (nil?     v) :initial
        :else        false))

(defn size? [v] ;; todo: support other units
  (cond (keyword? v) (in? v sizes globals)
        (ratio?   v) v
        (number?  v) v
        (ems?     v) v
        (points?  v) v
        (nil?     v) :initial
        :else        false))

(defn shadow? [v]
  (cond (vector?   v) (every? shadow? v)
        (keyword?  v) (in? v globals)
        (a/shadow? v) v
        (nil?      v) :initial
        :else         false))

(defn smoothing? [v]
  (cond (keyword? v) (in? v smoothings globals)
        (nil?     v) :initial
        :else        false))

(defn spacing? [v]
  (cond (keyword? v) (in? v spacings globals)
        (ratio?   v) v
        (number?  v) v
        (nil?     v) :initial
        :else        false))

(defn stretch? [v]
  (cond (keyword? v) (in? v stretches globals)
        (nil?     v) :initial
        :else        false))

(defn style? [v]
  (cond (keyword? v) (in? v styles globals)
        (nil?     v) :initial
        :else        false))

(defn synthesis? [v]
  (cond (keyword? v) (in? v syntheses globals)
        (nil?     v) :initial
        :else        false))

(defn transform? [v]
  (cond (keyword?     v) (in? v globals)
        (a/transform? v) v
        (nil?         v) :initial
        :else            false))

(defn origin? [v]
  (cond (keyword?     v) (in? v origins globals)
        ;(ratio? v)       :initial
        (nil?         v) :initial
        :else            false))

(defn box? [v]
  (cond (keyword? v) (in? v boxes globals)
        (nil?     v) :initial
        :else        false))

(defn txstyle? [v]
  (cond (keyword? v) (in? v txstyles globals)
        (nil?     v) :initial
        :else        false))

(defn decoration? [v]
  (cond (keyword? v) (in? v decorations globals)
        (nil?     v) :initial
        :else        false))

(defn weight? [v]
  (cond (keyword? v) (in? v weights globals)
        (nil?     v) :initial
        :else        false))

(def adjusts?     (validate-cells adjust?     "Error validating attribute of type adjust with value"))
(def alignhs?     (validate-cells alignh?     "Error validating attribute of type alingh with value"))
(def alignvs?     (validate-cells alignv?     "Error validating attribute of type alignv with value"))
(def colors?      (validate-cells color?      "Error validating attribute of type color with value"))
(def cursors?     (validate-cells cursor?     "Error validating attribute of type cursor with value"))
(def decorations? (validate-cells decoration? "Error validating attribute of type decoration with value"))
(def families?    (validate-cells family?     "Error validating attribute of type family with value"))
(def kernings?    (validate-cells kerning?    "Error validating attribute of type kerning with value"))
(def lengths?     (validate-cells length?     "Error validating attribute of type length with value"))
(def opacities?   (validate-cells opacity?    "Error validating attribute of type opacity with value"))
(def overflows?   (validate-cells overflow?   "Error validating attribute of type overflow with value"))
(def renderings?  (validate-cells rendering?  "Error validating attribute of type rendering with value"))
(def shadows?     (validate-cells shadow?     "Error validating attribute of type shadow with value"))
(def sizes?       (validate-cells size?       "Error validating attribute of type size with value"))
(def smoothings?  (validate-cells smoothing?  "Error validating attribute of type smoothing with value"))
(def spacings?    (validate-cells spacing?    "Error validating attribute of type spacing with value"))
(def stretches?   (validate-cells stretch?    "Error validating attribute of type stetch with value"))
(def styles?      (validate-cells style?      "Error validating attribute of type style with value"))
(def syntheses?   (validate-cells synthesis?  "Error validating attribute of type sythesis with value"))
(def transforms?  (validate-cells transform?  "Error validating attribute of type transformation with value"))
(def origins?     (validate-cells origin?     "Error validating attribute of type transformation origin with value"))
(def boxes?       (validate-cells box?        "Error validating attribute of type transformation box with value"))
(def txstyles?    (validate-cells txstyle?    "Error validating attribute of type transformation style with value"))
(def weights?     (validate-cells weight?     "Error validating attribute of type weight with value"))

(def callbacks?   (validate-cells callback?   "Error validating attribute of type callback with value"))
(def docks?       (validate-cells dock?       "Error validating attribute of type dock with value"))
(def attrs?       (validate-cells empty?      "Unhandled attribute with value"))

;;; attribute middlewares ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn align [ctor]
  "set the text-align and vertical-align attributes on the elem and proxy the
   vertical-align attribute to the outer element of each child.  set vertical
   height of the inner element to auto when the align vertical attribute is set.

  the vertical alignment is proxied to the outer elements of the children so
  that, in addition to aligning the lines of children within the elem, the
  children are also aligned in the same manner within their respective lines."
  (fn [{:keys [ah av] :as attrs} elems]
    {:pre [(alignhs? ah) (alignvs? av)]}
    (swap-elems! elems #(bind-in! %1 [out .-style .-verticalAlign] %2) (cell= (or av :top)))
    (with-let [e (ctor (dissoc attrs :ah :av) elems)]
      (bind-in! e [in  .-style .-height]     (cell= (if av :auto "100%"))) ;; initial instead? <--wrong!
      (bind-in! e [mid .-style .-textAlign]     ah)
      (bind-in! e [mid .-style .-verticalAlign] av))))

(defn color [ctor]
  "set the background color an the inner element."
  (fn [{:keys [c o m v] :as attrs} elems]
    {:pre [(colors? c) (opacities? o) (cursors? m)]}
    (with-let [e (ctor (dissoc attrs :c :o :m :v) elems)]
      (bind-in! e [mid .-style .-backgroundColor] c)
      (bind-in! e [mid .-style .-opacity]         o)
      (bind-in! e [mid .-style .-cursor]          m)
      (bind-in! e [out .-style .-display]        (cell= (if (and (contains? attrs :v) (not v)) :none :inline-table))))))

(defn transform [ctor]
  "set the background color an the inner element."
  (fn [{:keys [x xx xy xz xb xs] :as attrs} elems]
    {:pre [(transforms? x) (origins? xx xy xz) (boxes? xb) (txstyles? xs)]}
    (with-let [e (ctor (dissoc attrs :x :xx :xy :xz :xb :xs) elems)]
      (bind-in! e [out .-style .-transform]       x)
      (bind-in! e [out .-style .-transformOrigin] xx xy xz)
      (bind-in! e [out .-style .-transformBox]    xb)
      (bind-in! e [out .-style .-transformStyle]  xs))))

(defn nudge [ctor]
  "bump the position of an elem relative to its normal position in the layout.
   useful as a final tweak in cases where the correctly calculated position of
   an element may appear off visually.

   implemented by setting the margins on the elem's outer element."
  (fn [{:keys [nh nv] :as attrs} elems]
    {:pre [(lengths? nh nv)]}
    (with-let [e (ctor (dissoc attrs :nh :nv) elems)]
      (bind-in! e [out .-style .-margin] (or nv 0) (or (cell= (- nh)) 0) (or (cell= (- nv)) 0) (or nh 0)))))

(defn dock [ctor]
  "fix the element to the window."
  (fn [{:keys [xl xr xt xb] :as attrs} elems]
    {:pre [(docks? xl xr xt xb)]} ;; todo: warn about pct w, pct h
    (with-let [e (ctor (dissoc attrs :xl :xr :xt :xb) elems)]
      (bind-in! e [out .-style .-position] (cell= (if (or xl xr xt xb) :fixed :initial)))
      (bind-in! e [out .-style .-zIndex]   (cell= (if (or xl xr xt xb) "9999" :initial)))
      (bind-in! e [out .-style .-left]     (cell= (or xl nil)))
      (bind-in! e [out .-style .-right]    (cell= (or xr nil)))
      (bind-in! e [out .-style .-top]      (cell= (or xt nil)))
      (bind-in! e [out .-style .-bottom]   (cell= (or xb nil))))))

(defn overflow [ctor]
  "set the overflow style on the elem's middle element."
  (fn [{:keys [o oh ov] :as attrs} elems]
    {:pre [(overflows? o oh ov)]}
    (with-let [e (ctor (dissoc attrs :o :oh :ov) elems)]
      (bind-in! e [in .-style .-overflowX] (or oh o))
      (bind-in! e [in .-style .-overflowY] (or ov o)))))

(defn pad [ctor]
  "set the padding on the elem's inner element.

   this adds space between the edges of the container and its children."
  (fn [{:keys [p pl pr pt pb ph pv] :as attrs} elems]
    {:pre [(lengths? p pl pr pt pb ph pv)]}
    ;; todo: dissallow pct based paddings since tied to opposite dimension
    (with-let [e (ctor (dissoc attrs :p :pl :pr :pt :pb :ph :pv) elems)]
      (bind-in! e [mid .-style .-padding] (or pt pv p 0) (or pr ph p 0) (or pb pv p 0) (or pl ph p 0)))))

(defn round [ctor]
  "set the radius on the middle element."
  (fn [{:keys [r rtl rtr rbl rbr] :as attrs} elems]
    {:pre [(lengths? r rtl rtr rbl rbr)]}
    (with-let [e (ctor (dissoc attrs :r :rtl :rtr :rbl :rbr) elems)]
      (bind-in! e [mid .-style .-borderRadius] (or rtl r) (or rtr r) (or rbr r) (or rbl r)))))

(defn shadow [ctor]
  "set the shadows on the middle element."
  (fn [{:keys [d] :as attrs} elems]
    {:pre [(shadows? d)]}
    (with-let [e (ctor (dissoc attrs :d) elems)]
      (bind-in! e [mid .-style .-boxShadow] d))))

(defn size [ctor]
  "set the size on the outer element when it is expressed as a ratio, and on the
   inner element when it is a length.

   since ratios are expressed in terms of the parent, they include the margin
   (implemented as the padding between the inner and outer elements). fixed
   lengths are set on the middle, however, to exclude the margin so that when a
   margin is added, it will push out against the parent container instead of
   being subtracted from the size of the elem.  both the inner and middle
   elements are bound separately to accomodate cells that might return ratios,
   evals, and fixed sizes at different times, such as the cell returned by the
   breakpoints function."
  (fn [{:keys [w h] :as attrs} elems]
    {:pre [(lengths? w h)]}
    (with-let [e (ctor (dissoc attrs :w :h) elems)]
      (let [rel? #(or (ratio? %) (calc? %))
            rel   #(cell= (if (rel? %) % %2))
            fix   #(cell= (if (rel? %) %2 %))]
        (bind-in! e [out .-style .-width]     (rel w nil))
        (bind-in! e [out .-style .-height]    (rel h nil))
        (bind-in! e [mid .-style .-width]     (fix w nil))
        (bind-in! e [mid .-style .-height]    (fix h nil))
        (bind-in! e [mid .-style .-maxHeight] (fix h nil))))))

(defn space [ctor]
  "set the padding on the outer element of each child and a negative margin on
   the inner element of the elem itself equal to the padding.

   outer padding on the children creates an even gutter between them, while the
   negative inner margin on the elem itself offsets this padding to fencepost
   the children flush with the edges of the container."
  ;; todo: gutter between text nodes
  (fn [{:keys [g gh gv] :as attrs} elems]
    {:pre [(lengths? g gh gv)]}
    (let [mh (cell= (/ (or gh g) 2))
          mv (cell= (/ (or gv g) 2))
          ph (cell= (- mh))
          pv (cell= (- mv))]
      (swap-elems! elems #(bind-in! % [out .-style .-padding] %2 %3 %4 %5) mv mh mv mh)
      (with-let [e (ctor (dissoc attrs :g :gh :gv) elems)]
        (bind-in! e [in .-style .-margin] pv ph)))))

(defn stroke [ctor]
  "set the border on the elem's middle element.

   this adds space between the edges of the container and its children."
  (fn [{:keys [s sl sr st sb sh sv sc scl scr sct scb sch scv] :as attrs} elems]
    {:pre [(lengths? s sl sr st sb sh sv) (colors? sc scl scr sct scb sch scv)]}
    (with-let [e (ctor (dissoc attrs :s :sl :sr :st :sb :sh :sv :sw :sc :scl :scr :sct :scb :sch :scv) elems)]
      (bind-in! e [mid .-style .-borderWidth] (or st sv s 0)  (or sr sh s 0)  (or sb sv s 0)  (or sl sh s 0))
      (bind-in! e [mid .-style .-borderColor] (or sct scv sc) (or scr sch sc) (or scb scv sc) (or scl sch sc))
      (bind-in! e [mid .-style .-borderStyle] :solid))))

(defn font [ctor]
    "- f  font size
     - ft font weight
     - fw letter spacing
     - fh line height
     - ff font family
     - fc font color
     - fu text decoration
     - fi font style
     - fk font kerning
     - fr text rendering
     - fa font size adjust
     - fm font smoothing
     - fz font stretch
     - fy font synthesis"
  (fn [{:keys [f fw fh ft ff fc fu fi fk fa fs fy fr fm bh] :as attrs} elems]
    {:pre [(sizes? f bh) (spacings? fw fh) (weights? ft) (families? ff) (colors? fc) (decorations? fu) (styles? fi) (adjusts? fa) (stretches? fs) (syntheses? fy) (renderings? fr) (smoothings? fm)]}
    (with-let [e (ctor (dissoc attrs :f :fw :fh :ft :ff :fc :fu :fi :fk :fa :fs :fy :fr :fm bh) elems)]
      (bind-in! e [in .-style .-fontSize]               f)
      (bind-in! e [in .-style .-letterSpacing]          fw)
      (bind-in! e [in .-style .-lineHeight]             fh)
      (bind-in! e [in .-style .-fontWeight]             ft)
      (bind-in! e [in .-style .-fontFamily]             ff)
      (bind-in! e [in .-style .-color]                  fc)
      (bind-in! e [in .-style .-textDecoration]         fu)
      (bind-in! e [in .-style .-fontStyle]              fi)
      (bind-in! e [in .-style .-fontKerning]            fk)
      (bind-in! e [in .-style .-textRendering]          fr)
      (bind-in! e [in .-style .-fontSizeAdjust]         fa)
      (bind-in! e [in .-style .-webkitFontSmoothing]    fm)
      (bind-in! e [in .-style .-moz-osx-font-smoothing] (case fm :antialiased :greyscale :none :unset :initial))
      (bind-in! e [in .-style .-fontSmooth]             (case fm :antialiased :always    :none :never :initial))
      (bind-in! e [in .-style .-fontStretch]            fs)
      (bind-in! e [in .-style .-fontSynthesis]          fy))))

(defn destyle [ctor]
  "neutralize the default styling of the inner element.

  this allows native components to be styled freely using attributes mapped to
  the middle element."
  (fn [attrs elems]
    (with-let [e (ctor attrs elems)]
      (bind-in! e [in .-style .-width]           (r 1 1)) ;; display block should force to 100%
      (bind-in! e [in .-style .-height]          (r 1 1))
      (bind-in! e [in .-style .-outline]         :none)
      (bind-in! e [in .-style .-backgroundColor] :transparent)
      (bind-in! e [in .-style .-borderStyle]     :none)
      (bind-in! e [in .-style .-textAlign]       :inherit)))) ;; cursor: pointer, :width: 100%

;;; form middlewares ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:dynamic *data*   nil)
(def ^:dynamic *error*  nil)
(def ^:dynamic *submit* nil)

(defn form** [ctor]
  "set up a form context"
  (fn [{:keys [submit] :as attrs} elems]
    (reset! *submit* submit)
    (let [data *data*]
      (with-let [e (ctor (dissoc attrs :submit) elems)]
        (.addEventListener (in e) "keypress" #(when (= (.-which %) 13) (submit @data)))))))

(defn field' [ctor]
  "set up a form context"
  (fn [{:keys [autocorrect autocapitalize label key type value] :as attrs} elems]
    {:pre []} ;; todo: validate
    (let [data *data*]
      (with-let [e (ctor (dissoc attrs :autocorrect :autocapitalize :label :key :type :value) elems)]
        (.addEventListener (in e) "change" #(when data (swap! data assoc (keyword (.-name (in e))) (.-value (in e)))))
        (.addEventListener (in e) "keyup"  #(when data (swap! data assoc (keyword (.-name (in e))) (.-value (in e)))))
        (bind-in! e [in .-name]           (name key))
        (bind-in! e [in .-type]           type)
        (bind-in! e [in .-value]          value)
        (bind-in! e [in .-placeholder]    label)
        (bind-in! e [in .-autocorrect]    autocorrect)
        (bind-in! e [in .-autocapitalize] autocapitalize)))))

(defn submit' [ctor]
  "set up a form context"
  (fn [{label :label submit' :submit :as attrs} elems]
    {:pre []} ;; todo: validate
    (let [data   *data*
          submit *submit*]
      (with-let [e (ctor (dissoc attrs :label :submit) elems)]
        (.addEventListener (mid e) "click" #((or submit' @submit) @data))
        (bind-in! e [in .-type]  "submit")
        (bind-in! e [in .-value] label)))))

;;; middlewares ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn handle-exception [ctor]
  "handle errors by highlighting the corresponding component"
  (fn [attrs elems]
    (binding [*exceptions* (atom [])]
      (with-let [e (ctor attrs elems)]
        (when (not-empty @*exceptions*)
          (doseq [{:keys [msg]} @*exceptions*]
            (.log js/console msg))
          (bind-in! e [out .-title] (join "\n" (mapv :msg @*exceptions*)))
          (bind-in! e [out .-style .-border] 3 :solid :red))))))

(defn assert-noattrs [ctor]
  (fn [attrs elems]
    {:pre [(attrs? attrs)]}
    (ctor attrs elems)))

(defn skin [ctor]
  "add an svg skin to the component."
  (fn [attrs elems]
    (with-let [e (ctor attrs elems)]
      (let [skin (.createElementNS js/document "http://www.w3.org/2000/svg" "svg")] ;; if skin then hide mid styles
        (set! (.. skin -style -position) "absolute")
        (set! (.. skin -style -top)      "0")
        (set! (.. skin -style -left)     "0")
        (set! (.. skin -style -width)    "100%")
        (set! (.. skin -style -height)   "100%")
        (set! (.. skin -style -zIndex)   "-1")
        (set! (.. skin -innerHTML)       "<rect x='0' y='0' width='100%' height='100%' rx='10' ry='10' fill='#CCC' />")
        (.appendChild (mid e) skin)))))

(defn image* [ctor]
  ;; todo: vertical alignment of content
  "set the size of the absolutely positioned inner elem to the padding"
  (fn [{:keys [url p pl pr pt pb ph pv] :as attrs} elems]
    (with-let [e (ctor (dissoc attrs :url) elems)]
      (let [img (.insertBefore (mid e) (.createElement js/document "img") (in e))]
        (bind-in! img [.-style .-display]   :block)
        (bind-in! img [.-style .-position]  :relative)
        (bind-in! img [.-style .-width]     "100%")
        (bind-in! img [.-style .-height]    :initial)
        (bind-in! img [.-src]               url)
        (bind-in! e [in .-style .-position] :absolute)
        (bind-in! e [in .-style .-top]      0)
        (bind-in! e [in .-style .-width]    "100%")))))
      ; (bind-in! e [in .-style .-backgroundImage]  (when url (cell= (str "url(" url ")"))))
      ; (bind-in! e [in .-style .-backgroundSize]   (when url :contain))
      ; (bind-in! e [in .-style .-backgroundRepeat] (when url :no-repeat)))))

(defn click [ctor] ;; todo: remove listener
  (fn [{:keys [click] :as attrs} elems]
    {:pre [(callbacks? click)]}
    (with-let [e (ctor (dissoc attrs :click) elems)]
      (when click
        (.addEventListener (mid e) "click" click)))))

(defn parse-args [ctor]
  (fn [& args]
     (apply ctor (#'hoplon.core/parse-args args))))

(defn font-face [family style weight names urls ranges]
  {:pre [(family? family) (style? style) (weight? weight)]}
  (let [name  #(str "local('" % "')")
        url   #(str "url('" % "') format('" (re-find #".+\.([^?]+)(\?|$)" %) "')")
        src   (apply str (interpose "," (concat (map name names) (map url urls))))
        range (apply str (interpose "," ranges))
        props {"font-family"   family ;; ->elem
               "font-style"    style ;; ->elem
               "font-weight"   weight ;; ->elem
               "src"           src
               "unicode-range" range}]
    (str "@font-face{" (apply str (mapcat (fn [[k v]] (str k ":" v ";")  props))) "}")))

(defn button** [ctor]
  (fn [attrs elems]
    (with-let [e (ctor attrs elems)]
      (let [state hoplon.ui.attrs/*state*]
        (.addEventListener (mid e) "mouseover" #(reset! state :over))
        (.addEventListener (mid e) "mouseout"  #(reset! state :up))
        (.addEventListener (mid e) "mousedown" #(reset! state :down))
        (.addEventListener (mid e) "mouseup"   #(reset! state :up))))))

(defn window** [ctor]
  ;; todo: finish mousechanged
  (fn [{:keys [fonts icon language metadata route scroll scripts styles initiated mousechanged scrollchanged statuschanged routechanged o oh ov] :as attrs} elems]
    (let [get-agent  #(-> js/window .-navigator)
          get-hash   #(-> js/window .-location .-hash)
          get-route  #(-> js/window .-location .-hash hash->route)
          get-refer  #(-> js/window .-document .-referrer)
          get-status #(-> js/window .-document .-visibilityState visibility->status)]
        (with-let [e (ctor (dissoc attrs :fonts :icon :language :metadata :scroll :title :route :lang :styles :scripts :initiated :mousechanged :scrollchanged :statuschanged :routechanged) elems)]
          (bind-in! e [out .-lang] (or language "en"))
          (bind-in! e [out .-style .-width]    "100%")
          (bind-in! e [out .-style .-height]   "100%")
          (bind-in! e [mid .-style .-width]    "100%")
          (bind-in! e [mid .-style .-margin]   "0")
          (bind-in! e [mid .-style .-fontSize] "100%")
          (bind-in! e [mid .-style .-overflowX] (or oh o))
          (bind-in! e [mid .-style .-overflowY] (or ov o))
          (bind-in! e [in .-style .-overflowX] "")
          (bind-in! e [in .-style .-overflowY] "")
          (when initiated
            (initiated (get-route) (get-status) (get-agent) (get-refer)))
          (when routechanged
            (.addEventListener js/window "hashchange"
              #(when-not (= (route->hash @route) (get-hash)) (routechanged (get-route)))))
          (when statuschanged
            (.addEventListener js/window "visibilitychange"
              #(statuschanged (get-status))))
          (.addEventListener js/window "scroll"
            (let [scroll* *scroll*]
              #(let [[x y :as new-scroll] (vector (.-scrollX js/window) (.-scrollY js/window))]
                (reset! scroll* new-scroll)
                (when scrollchanged
                  (when-not (= new-scroll scroll)
                    (scrollchanged x y))))))
          (cell= (set! js/location.hash (route->hash route)))
          (.addEventListener js/document "DOMContentLoaded"
            #(cell= (.scroll js/window (first scroll) (second scroll))))
          (h/head
            (h/html-meta :charset "utf-8")
            (h/html-meta :http-equiv "X-UA-Compatible" :content "IE=edge")
            (h/html-meta :name "viewport"    :content "width=device-width, initial-scale=1")
            (for [m (if (map? metadata) (map (fn [[k v]] {:name k :content v}) metadata) metadata)]
              (h/html-meta (into {} (for [[k v] m] [k (name v)]))))
            (h/title (:title attrs))
            (h/link :rel "icon" :href (or icon empty-icon-url))
            (h/for-tpl [s styles]  (h/link :rel "stylesheet" :href s))
            (h/for-tpl [s scripts] (h/script :src s)))))))

(def component (comp handle-exception align shadow round stroke pad nudge size overflow dock font color transform click assert-noattrs))
(def img       (comp handle-exception align shadow round stroke image* pad nudge size overflow font color click))

;;; element primitives ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def window* (-> doc component space window** parse-args))
(def elem    (-> h/div    box component space parse-args))
(def button* (-> h/button box destyle component button** parse-args))
(def image   (-> h/div    box img parse-args))
(def form*   (-> h/form   box component space form** parse-args))
(def field   (-> h/input  box destyle component field' parse-args))
(def submit  (-> h/input  box destyle component submit' parse-args))

;;; todos ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; margin
;; fixed
;; constraint (absolute positioning within parent)
;; offset     (use outer css margin to move out of current position)
;; todo, once clear cases become apparent
;; baseline-shift
;; background, url (str "url(" v ") no-repeat 50% 50% / cover")
;; user-select, selectable
;; :toggle as as mid-attr
;; update, previously implemented on do multimethod, to form middleware
;; throw proper ui exceptions with stack traces and attribute kv information
