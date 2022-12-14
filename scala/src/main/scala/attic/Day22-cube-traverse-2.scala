package attic

import common._
import Day22_Problem1._

import scala.annotation.tailrec

object Day22_Problem2 extends MainBaseBig(22) {

  val Right = 0
  val Down  = 1
  val Left  = 2
  val Up    = 3

  val neighbours3dIncrements = List(
    Point3D(1, 0, 0),
    Point3D(-1, 0, 0),
    Point3D(0, 1, 0),
    Point3D(0, -1, 0),
    Point3D(0, 0, 1),
    Point3D(0, 0, -1)
  )

  def incrementToDirection(increment: Point): Int = {
    require(Math.abs(increment.l) + Math.abs(increment.c) == 1, increment)
    if (increment.c > 0) Right
    else if (increment.c < 0) Left
    else if (increment.l > 0) Down
    else Up
  }

  def computeCubeSize(map: Matrix[Char]): Int = {
    val nonSpaceCount = map.data.flatten.count(_ != ' ')

    (1 to 100).find(v => v * v == nonSpaceCount / 6).get
  }

  def findFacePoints(map: Matrix[Char], cubeSize: Int): Seq[Point] =
    for {
      l <- 0 until map.numLines() / cubeSize
      c <- 0 until map.numCols() / cubeSize
      p = Point(l * cubeSize, c * cubeSize)
      if map(p) != ' '
    } yield p

  // the cube is folded to the inside so to speak (or lateral faces coming to front)

  case class PlanePoints(
      planeVector: Point3D,
      contents: Map[Point3D, Point],
      foldRotations: List[Point3D]
  ) {
    require(Math.abs(planeVector.l) + Math.abs(planeVector.c) + Math.abs(planeVector.d) == 1)
  }

  /**
   * @param map
   * @return (folded cube, top left corner of the face used to found around
   */
  def foldCube(
      map: Matrix[Char]
  ): (Map[Point3D, PlanePoints], Point3D) = {
    val cubeSize    = computeCubeSize(map)
    val facePoints  = findFacePoints(map, cubeSize).map(p => Point3D(p.l, p.c, 0))
    val topLeftFace = facePoints.sortWith { case (a, b) => a.l < b.l || (a.l == b.l && a.c < b.c) }.head

    val unfoldedCube = facePoints.map { facePoint =>
      facePoint -> {
        val faceSlice =
          map.slice2D(Point(facePoint.l, facePoint.c), Point(facePoint.l + cubeSize, facePoint.c + cubeSize))
        PlanePoints(
          Point3D(0, 0, 1),
          faceSlice
            .values()
            .map { case (_, p) =>
              Point3D(p.l, p.c, 0).translate(facePoint) -> Point(p.l + facePoint.l, p.c + facePoint.c)
            }
            .toMap,
          Nil
        )

      }
    }.toMap

    (
      foldAroundFace(unfoldedCube, topLeftFace, cubeSize, Set.empty),
      topLeftFace
    )
  }

  def verifyCube(cube: Map[Point3D, PlanePoints]): Unit = {
    def projections(f: Iterable[Point3D]): (Matrix[Char], Matrix[Char], Matrix[Char]) = {
      val lc = f.map(v => Point(v.l, v.c))
      val dl = f.map(v => Point(v.d, v.l))
      val cd = f.map(v => Point(v.c, v.d))

      (
        Matrix.fromPoints(lc, '.', '*'),
        Matrix.fromPoints(dl, '.', '*'),
        Matrix.fromPoints(cd, '.', '*')
      )
    }

//    val firstCubeFace    = cube.keySet.find(_.d == 0).get
//    val allCubePoints    = cube.values.map(_.contents.keys).flatten
//    val shiftedCubeFaces = allCubePoints.map(p => p.translate(-firstCubeFace.l + 1, -firstCubeFace.c + 1, 0))

    cube.values.foreach { p =>
      val (a, b, c) = projections(p.contents.keys)
      println(s"Face ${p.planeVector}")
      println(a.mkString(""))
      println("---")
      println(b.mkString(""))
      println("---")
      println(c.mkString(""))
    }
  }

//  def render3d(cube: Map[Point3D, PlanePoints]): Unit = {
//    Slack3D("Rotating Box") foreach {
//      state =>
//        cube.values.map(_.contents.keys).flatten
//          .map {
//            p => Sphere(radius = 1, colour = Colour.Purple, center = Vector3(p.c, p.l, p.d))
//          }
//    }
//  }

  private def foldAroundFace(
      faces: Map[Point3D, PlanePoints],
      face: Point3D,
      cubeSize: Int,
      visitedFaces: Set[Point3D]
  ): Map[Point3D, PlanePoints] =
    if (visitedFaces.contains(face)) {
      Map.empty
    } else {
      val newVisitedFaces = visitedFaces + face

      // if there is a face to the right then fold that and the result around l axis by 90 deg
      val faceRight = Option(face.copy(c = face.c + cubeSize)).filter(f => faces.contains(f))
      val faceLeft  = Option(face.copy(c = face.c - cubeSize)).filter(f => faces.contains(f))
      val faceDown  = Option(face.copy(l = face.l + cubeSize)).filter(f => faces.contains(f))

      val foldedRight = faceRight.map(f => foldAroundFace(faces, f, cubeSize, newVisitedFaces)).filter(_.nonEmpty)
      val foldedLeft  = faceLeft.map(f => foldAroundFace(faces, f, cubeSize, newVisitedFaces)).filter(_.nonEmpty)
      val foldedDown  = faceDown.map(f => foldAroundFace(faces, f, cubeSize, newVisitedFaces)).filter(_.nonEmpty)

      val rotatedRight = foldedRight.map(f =>
        f.map { case (p, PlanePoints(planeVector, cubePoints, foldRotations)) =>
          p ->
            PlanePoints(
              planeVector.rotateAroundPoint(Point3D.zero, dl = 1, dc = 0, dd = 0),
              cubePoints.map { case (p3d, p2d) =>
                p3d
                  .translate(0, 1, 0)
                  .rotateAroundPoint(face.translate(0, cubeSize, 0), dl = 1, dc = 0, dd = 0) -> p2d
              },
              Point3D(1, 0, 0) +: foldRotations
            )
        }
      )

      val rotatedLeft = foldedLeft.map(f =>
        f.map { case (p, PlanePoints(planeVector, cubePoints, foldRotations)) =>
          p ->
            PlanePoints(
              planeVector.rotateAroundPoint(Point3D.zero, dl = 3, dc = 0, dd = 0),
              cubePoints.map { case (p3d, p2d) =>
                p3d
                  .translate(0, -1, 0)
                  .rotateAroundPoint(face.translate(0, -1, 0), dl = 3, dc = 0, dd = 0) -> p2d
              },
              Point3D(3, 0, 0) +: foldRotations
            )

        }
      )

      val rotatedDown = foldedDown.map(f =>
        f.map { case (p, PlanePoints(planeVector, cubePoints, foldRotations)) =>
          p ->
            PlanePoints(
              planeVector.rotateAroundPoint(Point3D.zero, dl = 0, dc = 3, dd = 0),
              cubePoints.map { case (p3d, p2d) =>
                p3d
                  .translate(1, 0, 0)
                  .rotateAroundPoint(face.translate(cubeSize, 0, 0), dl = 0, dc = 3, dd = 0) -> p2d
              },
              Point3D(0, 3, 0) +: foldRotations
            )

        }
      )

      Map(
        face -> faces(face)
      ) ++ rotatedRight.getOrElse(Map.empty) ++ rotatedLeft.getOrElse(Map.empty) ++ rotatedDown.getOrElse(Map.empty)
    }

  override def run(inputFile: List[String]): String = {
    val (map, cmds) = parse(inputFile)

    val (foldedCube, topLeftFace) = foldCube(map)
    val allFoldedCubePoints       = foldedCube.values.map(_.contents).flatten.toMap

//    verifyCube(foldedCube)
//    render3d(foldedCube)

    case class State(
        map: Matrix[Char],
        increment: Point3D,
        pos: Point3D
    )

    val initialState = State(
      map,
      increment = Point3D(0, 1, 0),
      pos = topLeftFace
    )

    def findFace(pos: Point3D): (Point3D, PlanePoints) =
      foldedCube.find { case (_, PlanePoints(_, contents, _)) =>
        contents.contains(pos)
      }.get

    val finalState = cmds.foldLeft(initialState) { case (acc, c) =>
      @tailrec
      def move(amount: Int, currentPos: Point3D, increment: Point3D): (Point3D, Point3D) = {
        println(s"pos:${currentPos}, increment: ${increment}, face: ${findFace(currentPos)._1}")
        if (amount == 0) (currentPos, increment)
        else {
          val newPos = currentPos.translate(increment)
          val (wrappedNewPos, newIncrement) = if (!allFoldedCubePoints.contains(newPos)) {
            // we need to wrap it
            val neighbours3d = neighbours3dIncrements
              .map(incr => (incr, newPos.translate(incr)))
              .filter(p => allFoldedCubePoints.contains(p._2))
              .filter(p => p._2 != currentPos)

            require(neighbours3d.size == 1, s"${acc.pos} -> ${neighbours3d}")
            neighbours3d.head.swap
          } else (newPos, increment)

          require(allFoldedCubePoints.contains(wrappedNewPos))

          if (map(allFoldedCubePoints(wrappedNewPos)) == '#') {
            (currentPos, increment)
          } else {
            move(amount - 1, wrappedNewPos, newIncrement)
          }
        }
      }

      def incrementToChar(increment: Point): Char =
        increment match {
          case Point(0,0) => 'X'
          case Point(1, 0)  => 'V'
          case Point(-1, 0) => '^'
          case Point(0, 1)  => '>'
          case Point(0, -1) => '<'
        }

      def updateMapWithDebug(oldPosition: Point3D, newPosition: Point3D): Matrix[Char] = {

        val newPosition2D = allFoldedCubePoints(newPosition)
        val oldPosition2D = allFoldedCubePoints(oldPosition)
        val increment2D   = Point(newPosition2D.l - oldPosition2D.l, newPosition2D.c - oldPosition2D.c)
        if (math.abs(increment2D.l) + math.abs(increment2D.c) > 1) {
          // we have a jump, so render the old as an ID and the new with the same ID
          acc.map
            .updated(oldPosition2D.l, oldPosition2D.c, 'O')
            .updated(newPosition2D.l, newPosition2D.c, 'O')
        } else {
          acc.map.updated(oldPosition2D.l, oldPosition2D.c, incrementToChar(increment2D))
        }
      }

      // rotating to the right is 27 degrees around the plane vector in trignometical way
      def incrementToRight(increment: Point3D): Point3D = {
        // determine the face vector
        val (_, PlanePoints(planeVector, _, _)) = findFace(acc.pos)

        require(Math.abs(planeVector.l) + math.abs(increment.l) <= 1)
        require(math.abs(planeVector.c) +  math.abs(increment.c) <= 1)
        require(math.abs(planeVector.d) + math.abs(increment.d) <= 1)

        increment.rotateAroundPoint(Point3D.zero, planeVector.l * 3, planeVector.c * 3, planeVector.d * 3)
      }

      // rotating to the left is 90 degres around the plane vector in trigonometrical way
      def incrementToLeft(increment: Point3D): Point3D = {
        // determine the face vector
        val (_, PlanePoints(planeVector, _, _)) = findFace(acc.pos)

        require(Math.abs(planeVector.l) + math.abs(increment.l) <= 1)
        require(math.abs(planeVector.c) +  math.abs(increment.c) <= 1)
        require(math.abs(planeVector.d) + math.abs(increment.d) <= 1)

        increment.rotateAroundPoint(Point3D.zero, planeVector.l, planeVector.c, planeVector.d)
      }

//      println(acc.map.mkString(""))

      c match {
        case Move(amount) =>
          val (newPosition, newIncrement) = move(amount, acc.pos, acc.increment)

          State(
            pos = newPosition,
            increment = newIncrement,
            map = updateMapWithDebug(newPosition, acc.pos)
          )
        case RotateRight => acc.copy(increment = incrementToRight(acc.increment))
        case RotateLeft  => acc.copy(increment = incrementToLeft(acc.increment))
      }
    }
    println(finalState.pos + "" + finalState.increment)

    // to determine final orientation we have to revert the folding for the current increment
    val (faceId, face) = findFace(finalState.pos)
    val unfoldedIncrement = face.foldRotations.foldLeft(finalState.increment) { case (acc, t) =>
      acc.rotateAroundPoint(Point3D.zero, t.l * -1, t.c * -1, t.d * -1)
    }
    require(unfoldedIncrement.d == 0, unfoldedIncrement)
    val direction = incrementToDirection(Point(unfoldedIncrement.l, unfoldedIncrement.c))

    val finalPos2D = allFoldedCubePoints(finalState.pos)

    val result = (finalPos2D.l + 1) * 1000 + (finalPos2D.c + 1) * 4 + direction
    result.toString
  }
}
