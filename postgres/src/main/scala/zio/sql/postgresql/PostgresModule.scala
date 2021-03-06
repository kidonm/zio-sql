package zio.sql.postgresql

import java.time.{ Instant, LocalDate, LocalTime, ZonedDateTime }
import zio.sql.Jdbc

/**
 */
trait PostgresModule extends Jdbc { self =>

  object PostgresFunctionDef {
    val Localtime                   = FunctionDef[Nothing, LocalTime](FunctionName("localtime"))
    val LocaltimeWithPrecision      = FunctionDef[Int, LocalTime](FunctionName("localtime"))
    val Localtimestamp              = FunctionDef[Nothing, Instant](FunctionName("localtimestamp"))
    val LocaltimestampWithPrecision = FunctionDef[Int, Instant](FunctionName("localtimestamp"))
    val Md5                         = FunctionDef[String, String](FunctionName("md5"))
    val ParseIdent                  = FunctionDef[String, String](FunctionName("parse_ident"))
    val Chr                         = FunctionDef[Int, String](FunctionName("chr"))
    val CurrentDate                 = FunctionDef[Nothing, LocalDate](FunctionName("current_date"))
    val Initcap                     = FunctionDef[String, String](FunctionName("initcap"))
    val Repeat                      = FunctionDef[(String, Int), String](FunctionName("repeat"))
    val Reverse                     = FunctionDef[String, String](FunctionName("reverse"))
    val TrimScale                   = FunctionDef[Double, Double](FunctionName("trim_scale"))
    val Hex                         = FunctionDef[Int, String](FunctionName("to_hex"))
    val Left                        = FunctionDef[(String, Int), String](FunctionName("left"))
    val Length                      = FunctionDef[String, Int](FunctionName("length"))
    val MinScale                    = FunctionDef[Double, Int](FunctionName("min_scale"))
    val Radians                     = FunctionDef[Double, Double](FunctionName("radians"))
    val Right                       = FunctionDef[(String, Int), String](FunctionName("right"))
    val StartsWith                  = FunctionDef[(String, String), Boolean](FunctionName("starts_with"))
    val Translate                   = FunctionDef[(String, String, String), String](FunctionName("translate"))
    val Trunc                       = FunctionDef[Double, Double](FunctionName("trunc"))
    val Sind                        = FunctionDef[Double, Double](FunctionName("sind"))
    val GCD                         = FunctionDef[(Double, Double), Double](FunctionName("gcd"))
    val LCM                         = FunctionDef[(Double, Double), Double](FunctionName("lcm"))
    val CBRT                        = FunctionDef[Double, Double](FunctionName("cbrt"))
    val Degrees                     = FunctionDef[Double, Double](FunctionName("degrees"))
    val Div                         = FunctionDef[(Double, Double), Double](FunctionName("div"))
    val Factorial                   = FunctionDef[Int, Int](FunctionName("factorial"))
    val Random                      = FunctionDef[Nothing, Double](FunctionName("random"))
    val LPad                        = FunctionDef[(String, Int, String), String](FunctionName("lpad"))
    val RPad                        = FunctionDef[(String, Int, String), String](FunctionName("rpad"))
    val ToTimestamp                 = FunctionDef[Long, ZonedDateTime](FunctionName("to_timestamp"))
    val PgClientEncoding            = FunctionDef[Nothing, String](FunctionName("pg_client_encoding"))
  }

  override def renderUpdate(update: self.Update[_]): String = {
    val builder = new StringBuilder

    def buildUpdateString[A <: SelectionSet[_]](update: self.Update[_]): Unit =
      update match {
        case Update(table, set, whereExpr) =>
          builder.append("UPDATE ")
          buildTable(table)
          builder.append("SET ")
          buildSet(set)
          builder.append("WHERE ")
          buildExpr(whereExpr, builder)
      }

    def buildTable(table: Table): Unit =
      table match {
        //The outer reference in this type test cannot be checked at run time?!
        case sourceTable: self.Table.Source =>
          val _ = builder.append(sourceTable.name)
        case Table.Joined(_, left, _, _)    =>
          buildTable(left) //TODO restrict Update to only allow sourceTable
      }

    def buildSet[A <: SelectionSet[_]](set: List[Set[_, A]]): Unit =
      set match {
        case head :: tail =>
          buildExpr(head.lhs, builder)
          builder.append(" = ")
          buildExpr(head.rhs, builder)
          tail.foreach { setEq =>
            builder.append(", ")
            buildExpr(setEq.lhs, builder)
            builder.append(" = ")
            buildExpr(setEq.rhs, builder)
          }
        case Nil          => //TODO restrict Update to not allow empty set
      }

    buildUpdateString(update)
    builder.toString()
  }

  private def buildExpr[A, B](expr: self.Expr[_, A, B], builder: StringBuilder): Unit = expr match {
    case Expr.Source(tableName, column)                                            =>
      val _ = builder.append(tableName).append(".").append(column.name)
    case Expr.Unary(base, op)                                                      =>
      val _ = builder.append(" ").append(op.symbol)
      buildExpr(base, builder)
    case Expr.Property(base, op)                                                   =>
      buildExpr(base, builder)
      val _ = builder.append(" ").append(op.symbol)
    case Expr.Binary(left, right, op)                                              =>
      buildExpr(left, builder)
      builder.append(" ").append(op.symbol).append(" ")
      buildExpr(right, builder)
    case Expr.Relational(left, right, op)                                          =>
      buildExpr(left, builder)
      builder.append(" ").append(op.symbol).append(" ")
      buildExpr(right, builder)
    case Expr.In(value, set)                                                       =>
      buildExpr(value, builder)
      buildReadString(set, builder)
    case Expr.Literal(value)                                                       =>
      val _ = builder.append(value.toString) //todo fix escaping
    case Expr.AggregationCall(param, aggregation)                                  =>
      builder.append(aggregation.name.name)
      builder.append("(")
      buildExpr(param, builder)
      val _ = builder.append(")")
    case Expr.FunctionCall0(function) if function.name.name == "localtime"         =>
      val _ = builder.append(function.name.name)
    case Expr.FunctionCall0(function) if function.name.name == "localtimestamp"    =>
      val _ = builder.append(function.name.name)
    case Expr.FunctionCall0(function) if function.name.name == "current_date"      =>
      val _ = builder.append(function.name.name)
    case Expr.FunctionCall0(function) if function.name.name == "current_timestamp" =>
      val _ = builder.append(function.name.name)
    case Expr.FunctionCall0(function)                                              =>
      builder.append(function.name.name)
      builder.append("(")
      val _ = builder.append(")")
    case Expr.FunctionCall1(param, function)                                       =>
      builder.append(function.name.name)
      builder.append("(")
      buildExpr(param, builder)
      val _ = builder.append(")")
    case Expr.FunctionCall2(param1, param2, function)                              =>
      builder.append(function.name.name)
      builder.append("(")
      buildExpr(param1, builder)
      builder.append(",")
      buildExpr(param2, builder)
      val _ = builder.append(")")
    case Expr.FunctionCall3(param1, param2, param3, function)                      =>
      builder.append(function.name.name)
      builder.append("(")
      buildExpr(param1, builder)
      builder.append(",")
      buildExpr(param2, builder)
      builder.append(",")
      buildExpr(param3, builder)
      val _ = builder.append(")")
    case Expr.FunctionCall4(param1, param2, param3, param4, function)              =>
      builder.append(function.name.name)
      builder.append("(")
      buildExpr(param1, builder)
      builder.append(",")
      buildExpr(param2, builder)
      builder.append(",")
      buildExpr(param3, builder)
      builder.append(",")
      buildExpr(param4, builder)
      val _ = builder.append(")")
  }

  private def buildExprList(expr: List[Expr[_, _, _]], builder: StringBuilder): Unit =
    expr match {
      case head :: tail =>
        buildExpr(head, builder)
        tail match {
          case _ :: _ =>
            builder.append(", ")
            buildExprList(tail, builder)
          case Nil    => ()
        }
      case Nil          => ()
    }

  private def buildOrderingList(expr: List[Ordering[Expr[_, _, _]]], builder: StringBuilder): Unit =
    expr match {
      case head :: tail =>
        head match {
          case Ordering.Asc(value)  => buildExpr(value, builder)
          case Ordering.Desc(value) =>
            buildExpr(value, builder)
            builder.append(" DESC")
        }
        tail match {
          case _ :: _ =>
            builder.append(", ")
            buildOrderingList(tail, builder)
          case Nil    => ()
        }
      case Nil          => ()
    }

  private def buildSelection[A](selectionSet: SelectionSet[A], builder: StringBuilder): Unit =
    selectionSet match {
      case cons0 @ SelectionSet.Cons(_, _) =>
        object Dummy {
          type Source
          type A
          type B <: SelectionSet[Source]
        }
        val cons = cons0.asInstanceOf[SelectionSet.Cons[Dummy.Source, Dummy.A, Dummy.B]]
        import cons._
        buildColumnSelection(head, builder)
        if (tail != SelectionSet.Empty) {
          builder.append(", ")
          buildSelection(tail, builder)
        }
      case SelectionSet.Empty              => ()
    }

  private def buildColumnSelection[A, B](columnSelection: ColumnSelection[A, B], builder: StringBuilder): Unit =
    columnSelection match {
      case ColumnSelection.Constant(value, name) =>
        builder.append(value.toString) //todo fix escaping
        name match {
          case Some(name) =>
            val _ = builder.append(" AS ").append(name)
          case None       => ()
        }
      case ColumnSelection.Computed(expr, name)  =>
        buildExpr(expr, builder)
        name match {
          case Some(name) =>
            Expr.exprName(expr) match {
              case Some(sourceName) if name != sourceName =>
                val _ = builder.append(" AS ").append(name)
              case _                                      => ()
            }
          case _          => () //todo what do we do if we don't have a name?
        }
    }

  private def buildTable(table: Table, builder: StringBuilder): Unit =
    table match {
      //The outer reference in this type test cannot be checked at run time?!
      case sourceTable: self.Table.Source          =>
        val _ = builder.append(sourceTable.name)
      case Table.Joined(joinType, left, right, on) =>
        buildTable(left, builder)
        builder.append(joinType match {
          case JoinType.Inner      => " INNER JOIN "
          case JoinType.LeftOuter  => " LEFT JOIN "
          case JoinType.RightOuter => " RIGHT JOIN "
          case JoinType.FullOuter  => " OUTER JOIN "
        })
        buildTable(right, builder)
        builder.append(" ON ")
        buildExpr(on, builder)
        val _ = builder.append(" ")
    }

  private def buildReadString[A <: SelectionSet[_]](read: self.Read[_], builder: StringBuilder): Unit =
    read match {
      case read0 @ Read.Select(_, _, _, _, _, _, _, _) =>
        object Dummy {
          type F
          type A
          type B <: SelectionSet[A]
        }
        val read = read0.asInstanceOf[Read.Select[Dummy.F, Dummy.A, Dummy.B]]
        import read._

        builder.append("SELECT ")
        buildSelection(selection.value, builder)
        builder.append(" FROM ")
        buildTable(table, builder)
        whereExpr match {
          case Expr.Literal(true) => ()
          case _                  =>
            builder.append(" WHERE ")
            buildExpr(whereExpr, builder)
        }
        groupBy match {
          case _ :: _ =>
            builder.append(" GROUP BY ")
            buildExprList(groupBy, builder)

            havingExpr match {
              case Expr.Literal(true) => ()
              case _                  =>
                builder.append(" HAVING ")
                buildExpr(havingExpr, builder)
            }
          case Nil    => ()
        }
        orderBy match {
          case _ :: _ =>
            builder.append(" ORDER BY ")
            buildOrderingList(orderBy, builder)
          case Nil    => ()
        }
        limit match {
          case Some(limit) =>
            builder.append(" LIMIT ").append(limit)
          case None        => ()
        }
        offset match {
          case Some(offset) =>
            val _ = builder.append(" OFFSET ").append(offset)
          case None         => ()
        }

      case Read.Union(left, right, distinct) =>
        buildReadString(left, builder)
        builder.append(" UNION ")
        if (!distinct) builder.append("ALL ")
        buildReadString(right, builder)

      case Read.Literal(values) =>
        val _ = builder.append(" (").append(values.mkString(",")).append(") ") //todo fix needs escaping
    }

  private def buildDeleteString(delete: self.Delete[_], builder: StringBuilder): Unit = {
    import delete._

    builder.append("DELETE FROM ")
    buildTable(table, builder)
    whereExpr match {
      case Expr.Literal(true) => ()
      case _                  =>
        builder.append(" WHERE ")
        buildExpr(whereExpr, builder)
    }
  }

  override def renderDelete(delete: self.Delete[_]): String = {
    val builder = new StringBuilder()

    buildDeleteString(delete, builder)
    builder.toString()
  }

  override def renderRead(read: self.Read[_]): String = {
    val builder = new StringBuilder()

    buildReadString(read, builder)
    builder.toString()
  }
}
