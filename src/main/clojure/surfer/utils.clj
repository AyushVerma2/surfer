(ns surfer.utils
  (:import 
    [java.security MessageDigest]
    [java.util UUID]
    [java.nio.charset StandardCharsets]
    [org.bouncycastle.crypto.digests KeccakDigest] ))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

(def array-of-bytes-type (Class/forName "[B")) 

(def EMPTY-BYTES (byte-array 0))

(defn to-bytes ^bytes [data]
  (cond
    (string? data) (.getBytes ^String data StandardCharsets/UTF_8)
    (= (class data) array-of-bytes-type) data
    (nil? data) EMPTY-BYTES
    :else (throw (IllegalArgumentException. (str "Can't convert to bytes: " (class data))))))

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
