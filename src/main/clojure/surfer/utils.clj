(ns surfer.utils
  "Utility functions for Ocean Ecosystem and Marketplaces"
  (:import
    [java.security MessageDigest]
    [java.util UUID]
    [java.nio.charset StandardCharsets]
    [org.bouncycastle.crypto.digests KeccakDigest] )
  (:import [java.time Instant]
           [java.io InputStream ByteArrayOutputStream]
           [java.util Date]
           [java.sql Timestamp]
           [org.apache.tika.mime MimeTypes MimeTypeException]))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

(def array-of-bytes-type (Class/forName "[B"))

(def EMPTY-BYTES (byte-array 0))

;; ==================================================
;; Time handling

(defn to-instant ^java.time.Instant [ob]
  (cond
    (instance? java.time.Instant ob) ob
    (instance? java.sql.Timestamp ob) (.toInstant ^java.sql.Timestamp ob)
    (instance? java.util.Date ob) (.toInstant ^java.util.Date ob)
    :else nil))

;; ==================================================
;; Bytes

(defn to-bytes ^bytes [data]
  (cond
    (string? data) (.getBytes ^String data StandardCharsets/UTF_8)
    (= (class data) array-of-bytes-type) data
    (nil? data) EMPTY-BYTES
    :else (throw (IllegalArgumentException. (str "Can't convert to bytes: " (class data))))))

(defn bytes-from-stream
  "Fully reads an input stream into an array of bytes"
  (^bytes [^InputStream is]
    (let [max-size 10000000 ;; limit for loading
          ^ByteArrayOutputStream bos (ByteArrayOutputStream.)
          buff (byte-array 10000)]
      (loop [loaded 0]
        (when (> loaded max-size) (throw (Error. (str "Too much data, max size: " max-size))))
        (let [len (int (.read is buff))]
          (when (>= len 0)
            (.write bos buff (int 0) (int len))
            (recur (+ loaded len)))))
      (.toByteArray bos))))


;; ======================================================
;; Hex utilities

(defn hex [i]
  (.charAt "0123456789abcdef" (mod i 16)))

(defn byte-to-hex [^long b]
  (str (hex (unsigned-bit-shift-right b 4)) (hex b) ))

(defn hex-string [^bytes data]
  (apply str (map byte-to-hex data)))

(defn sha256
  "Compute sha256 hash of a message.

   Returns an array of 32 bytes."
  [msg]
  (let [data (to-bytes msg)
        md (MessageDigest/getInstance "SHA-256")]
    (.digest md data)))

(defn keccak256
  "Compute keccak256 hash of a message.

   Returns an array of 32 bytes."
  [msg]
  (let [data (to-bytes msg)
        md (KeccakDigest. 256)
        result (byte-array 32)]
    (.update md data (int 0) (int (count data)))
    (.doFinal md result 0)
    result))

(defn remove-nil-values
  "Removes nil values from a map. Useful for eliminating blank optional values."
  ([m]
    (reduce (fn [m [k v]] (if (nil? v) (dissoc m k) m)) m m)))


;; ==================================================
;; Identifiers

(defn valid-id-char?
  "Returns true if c is a valid lowercase hex character."
  ([^long c]
    (or
      (and (<= 48 c) (<= c 57)) ;; digit
      (and (<= 97 c) (<= c 102));; lowercase a-f
      )))

(defn valid-id?
  "Returns true iff given a valid asset id string."
  ([^String s ^long len]
    (and
      (string? s)
      (== len (count s))
      (loop [i (int 0)]
        (if (< i 53)
          (let [c (int (.charAt s i))
                c (long c)]
            (if (valid-id-char? c)
              (recur (inc i))
              false))
          true)))))

(defn valid-user-id?
  "Returns true iff given a valid user id string."
  ([id]
    (valid-id? id 64)))

(defn valid-asset-id?
  "Returns true iff given a valid asset id string."
  ([id]
    (valid-id? id 64)))

(defn valid-listing-id?
  "Returns true iff given a valid listing id string."
  ([id]
    (valid-id? id 64)))

(defn valid-purchase-id?
  "Returns true iff given a valid purchase id string."
  ([id]
    (valid-id? id 64)))

(defn parse-bigdecimal
  "Attempts to parse a string to a BigDecimal value. Returns nil if not possible."
  ([s]
    (try
      (java.math.BigDecimal. (str s))
      (catch java.lang.NumberFormatException e nil))))

(defn valid-token-value?
  "Returns true if and only if the input is a valid Token value"
  ([s]
    (boolean
      (parse-bigdecimal s))))


(defn new-random-id
  "Creates a new random hex ID of the given length.

   Default length is 64."
  ([] (new-random-id 64))
  ([^long length]
    (let [uuid (UUID/randomUUID)
         hash (keccak256 (str uuid))
         hex (hex-string hash)
         bs (* 64 (quot (dec length) 64))
         tail (subs hex 0 (- length bs))]
      (if (> bs 0)
        (str (new-random-id bs) tail)
        tail))))

;; ==================================================
;; content type

(def ^MimeTypes mime-type-registry (MimeTypes/getDefaultMimeTypes))

(def default-extension ".bin") ;; for application/octet-stream

(defn ext-for-content-type
  "Return the appropriate extension for a given content type
  (defaults to 'bin' if the content-type is not recognized)"
  [content-type]
  (try
    (let [mime-type (.forName mime-type-registry content-type)
          ext (if mime-type (.getExtension mime-type))]
      (if (empty? ext)
        default-extension
        ext))
    (catch MimeTypeException _
      default-extension)))
