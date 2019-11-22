(ns surfer.app-context
  "App Context is a subset of this System's components.

   --
   Component Usage Notes
   https://github.com/stuartsierra/component#usage-notes

   # Do not pass the system around.
   The top-level \"system\" record is used only
   for starting and stopping other components, and for convenience during
   interactive development.

   # No function should take the entire system as an argument Application
   functions should never receive the whole system as an argument. This is
   unnecessary sharing of global state. Rather, each function should be defined
   in terms of at most one component. If a function depends on several
   components, then it should have its own component with dependencies on the
   things it needs.

   # No component should be aware of the system which contains it Each
   component receives references only to the components on which it depends.")

(defn new-context [env database starfish]
  #:app-context{:env env
                :database database
                :starfish starfish})

(defn database
  "Database Component

   Abstraction over the concrete database implementation.

   See `surfer.component.web-server` namespace."
  [app-context]
  (:app-context/database app-context))

(defn env
  "Env component

   See `surfer.component.web-server` namespace."
  [app-context]
  (:app-context/env app-context))
