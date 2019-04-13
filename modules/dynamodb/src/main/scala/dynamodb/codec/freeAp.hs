{-# Language GADTs #-}

data FreeAp f a where
  Lift :: f a -> FreeAp f a
  Map :: (a -> b) -> FreeAp f a -> FreeAp f b
  Unit :: FreeAp f ()
  Product :: FreeAp f a -> FreeAp f b -> FreeAp f (a, b)

instance Functor (FreeAp f) where
  fmap f fa = Map f fa

instance Applicative (FreeAp f) where
  pure a = Map (\_ -> a) Unit
  fab <*> fa = Map (uncurry ($)) (Product fab fa)

instance Show (FreeAp f a) where
 show (Lift fa) = "Lift(..)"
 show (Map f fa) = "Map(λ," ++ show fa ++  ")"
 show Unit = "Unit"
 show (Product fa fb) = "Product(" ++ show fa ++ "," ++ show fb ++ ")"

lift :: f a -> FreeAp f a
lift = Lift

(*:) :: FreeAp f a -> FreeAp f b -> FreeAp f (a, b)
(*:) = Product

unit :: FreeAp f ()
unit = Unit

assocl :: (a, (b, c)) -> ((a,b), c)
assocl (x, (y, z)) = ((x, y), z)

(***) :: (a -> b) -> (c -> d) -> (a, c) -> (b, d)
(***) f g (x, y) = (f x, g y)

{-
 Laws

 (1) map id u = u                                       (Functor identity)
 (2) map (f . g) u = map f (map g u)                    (Functor composition)
 (3) map (f *** g) (u *: v) = map f u *: map g v        (naturality of *:)
 (4) map snd (unit *: v) = v                            (left identity)
 (5) map fst (u *: unit) = u                            (right identity)
 (6) map assocl (u *: (v *: w)) = (u *: v) *: w         (associativity)
-}

{-
 Inspired by 'Lifting Operators and Laws' by Ralf Hinze, except we
want to product a right associated tree, not a left associated one.
The algorithm proceeds in two steps. First, it moves all occurrences
of `Map` to the front: `e` transformed into `Map f u`` where `u` is a
nested pair of lifts and units. Second, we turn u into a right-linear
tree, that is, a ‘cons-list’ of `Lift`s. Both transformations return a
term of the form `Map f u`. Each step is justified by applying the laws.
-}

norm :: FreeAp f a -> FreeAp f a
norm e = case norm1 e of
  Map f u ->
    case norm2 u of
      Map g v -> Map (f . g) v  -- (2)
  where
    norm1 :: FreeAp f a -> FreeAp f a
    norm1 (Lift fa) = Map id (Lift fa) -- (1)
    norm1 (Map f e) =
     case norm1 e of
       (Map g u) ->  Map(f . g) u  -- (2)
    norm1 Unit = Map id Unit  -- (1)
    norm1 (Product e1 e2) =
      case (norm1 e1, norm1 e2) of
        (Map f1 u1, Map f2 u2) -> Map (f1 *** f2) (Product u1 u2) -- (3)

    norm2 :: FreeAp f a -> FreeAp f a
    norm2 (Lift fa) = Map id (Lift fa)  -- (1)
    norm2 Unit = Map id Unit  -- (1)
    norm2 (Product Unit e2) =
      case norm2 e2 of
        Map f2 u2 ->  Map (\x -> ((), f2 x)) u2 -- (4)
    norm2 (Product e1 Unit) =
      case norm2 e1 of
        Map f1 u1 ->  Map (\x -> (f1 x, ())) u1  -- (5)
    norm2 (Product (Lift fa) e2) =
      case norm2 e2 of
        Map f2 u2 ->  Map (id *** f2) (Product (Lift fa) u2) -- (2), (3)
    norm2 (Product (Product e1  e2) e3) =
      case norm2  (Product e1 (Product e2 e3)) of
        Map f u -> Map (assocl . f) u   -- (6)


newtype Id a = Id a

data User = User Int String

data Role = Role String User
data Role2 = Role2 User String

p = (,) <$> lift (Id "a") <*> pure 3
p2 = User <$> lift (Id 1) <*> lift (Id "age")
p3 = Role <$> lift (Id "role") <*> p2
p4 = Role2 <$> p2 <*> lift (Id "role")
p5 = Role2 <$> p2 <*> pure "role"
