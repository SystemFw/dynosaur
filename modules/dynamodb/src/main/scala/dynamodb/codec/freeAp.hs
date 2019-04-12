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

norm :: FreeAp f a -> FreeAp f a
norm e = case norm1 e of
  Map f u ->
    case norm2 u of
      Map g v -> Map (f . g) v

norm1 :: FreeAp f a -> FreeAp f a
norm1 (Lift fa) = Map id (Lift fa)
norm1 (Map f e) =
 case norm1 e of
   (Map g u) ->  Map(f . g) u
norm1 Unit = Map id Unit
norm1 (Product e1 e2) =
  case (norm1 e1, norm1 e2) of
    (Map f1 u1, Map f2 u2) -> Map (f1 *** f2) (Product u1 u2)

norm2 :: FreeAp f a -> FreeAp f a
norm2 (Lift fa) = Map id (Lift fa)
norm2 Unit = Map id Unit
norm2 (Product Unit e2) = 
  case norm2 e2 of
    Map f2 u2 ->  Map (\x -> ((), f2 x)) u2
norm2 (Product e1 Unit) = 
  case norm2 e1 of
    Map f1 u1 ->  Map (\x -> (f1 x, ())) u1
norm2 (Product e1 (Lift fa)) =
  case norm2 e1 of
    Map f1 u1 ->  Map (f1 *** id) (Product u1 $ Lift fa)
norm2 (Product e1 (Product e2 e3)) =
  case norm2 (Product (Product e1  e2) e3) of
    Map f u -> Map (assocr . f) u

assocr ((x, y), z) = (x, (y, z))
(***) f g (x, y) = (f x, g y)

instance Show (FreeAp f a) where
 show (Lift fa) = "Lift(..)"
 show (Map f fa) = "Map(λ," ++ show fa ++  ")"
 show Unit = "Unit"
 show (Product fa fb) = "Product(" ++ show fa ++ "," ++ show fb ++ ")"



lift :: f a -> FreeAp f a
lift = Lift

newtype Id a = Id a

data User = User Int String

data Role = Role String User
data Role2 = Role2 User String

p = (,) <$> lift (Id "a") <*> pure 3
p2 = User <$> lift (Id 1) <*> lift (Id "age")
p3 = Role <$> lift (Id "role") <*> p2
p4 = Role2 <$> p2 <*> lift (Id "role")
p5 = Role2 <$> p2 <*> pure "role"



-- norm :: Term ρ α → Term ρ α
-- norm e = case norm1 e of Map f u → case norm2 u of Map g v → Map (f ·g) v
-- ----
-- norm1 :: Term ρ α → Term ρ α
-- norm1 (Var n) = Map id (Var n) -- functor identity
-- norm1 (Map f e) = -- functor composition
--  case norm1 e of Map g u → Map(f ·g) u
-- norm1 Unit = Map id Unit -- functor identity
-- norm1 (e1 :⋆ e2) = -- naturality of ⋆
--  case (norm1 e1,norm1 e2) of (Map f1 u1, Map f2 u2) → Map (f1 × f2) (u1 :⋆ u2)
-- ----
-- norm2 :: Term ρ α →Term ρ α
-- norm2 (Var n) = Map id (Var n) -- functor identity
-- norm2 Unit =  Map id Unit -- functor identity
-- norm2 (Unit :⋆ e2) =
--   case norm2 e2 of Map f2 u2 → Map (const () △ f2) u2 -- left identity
-- norm2 (e1 :⋆ Unit) =
--   case norm2 e1 of Map f1 u1 → Map (f1 △ const ()) u1 -- right identity
-- norm2 (e1 :⋆ Var n) =
--   case norm2 e1 of Map f1 u1 → Map (f1 × id) (u1 :⋆ Var n)
-- norm2 (e1 :⋆ (e2 :⋆ e3)) =
--   case norm2 ((e1 :⋆e2):⋆e3) of Map f u → Map (assocr·f) u -- associativity
-- ----
-- id x = x
-- (f ·g)x =f (g x)
-- const x y = x
-- flip f x y = f y x
-- fst (x,y) = x
-- snd (x,y) = y
-- (f △ g) x = (f x, g x)
-- (f × g) (x,y) = (f x, g y)
-- assocl (x, (y,z)) = ((x,y),z)
-- assocr ((x, y), z) = (x, (y, z))
-- app (f,x) = f x
-- curry f x y = f (x,y)



