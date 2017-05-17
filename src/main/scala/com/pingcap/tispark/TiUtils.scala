package com.pingcap.tispark

import com.google.proto4pingcap.ByteString
import org.apache.spark.sql.catalyst.expressions.aggregate._
import org.apache.spark.sql.catalyst.expressions.{Expression, IntegerLiteral, NamedExpression}
import org.apache.spark.sql.catalyst.planning.{PhysicalAggregation, PhysicalOperation}
import org.apache.spark.sql.catalyst.plans.logical
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan
import org.apache.spark.sql.execution.datasources.LogicalRelation
import org.apache.spark.sql.sources.CatalystSource


object TiUtils {
  def isSupportedLogicalPlan(plan: LogicalPlan): Boolean = {
    plan match {
      case PhysicalAggregation(
      groupingExpressions, aggregateExpressions, _, child) =>
        !aggregateExpressions.exists(expr => !isSupportedAggregate(expr)) &&
          !groupingExpressions.exists(expr => !isSupportedGroupingExpr(expr)) &&
          isSupportedLogicalPlan(child)

      case PhysicalOperation(projectList, filters, child) if (child ne plan) =>
        isSupportedPhysicalOperation(plan, projectList, filters, child)

      case logical.ReturnAnswer(rootPlan) => rootPlan match {
        case logical.Limit(IntegerLiteral(_), logical.Sort(_, true, child)) =>
          isSupportedPlanWithDistinct(child)
        case logical.Limit(IntegerLiteral(_),
        logical.Project(_, logical.Sort(_, true, child))) =>
          isSupportedPlanWithDistinct(child)
        case logical.Limit(IntegerLiteral(_), child) =>
          isSupportedPlanWithDistinct(child)
        case _ => false
      }

      case LogicalRelation(_: CatalystSource, _, _) => true

      case _ => false
    }
  }

  private def isSupportedPhysicalOperation(currentPlan: LogicalPlan,
                                           projectList: Seq[NamedExpression],
                                           filterList: Seq[Expression],
                                           child: LogicalPlan): Boolean = {
    // It seems Spark return the plan itself if no match instead of fail
    // So do a test avoiding unlimited recursion
    !projectList.exists(expr => !isSupportedProjection(expr)) &&
      !filterList.exists(expr => !isSupportedFilter(expr)) &&
      isSupportedLogicalPlan(child)
  }

  private def isSupportedPlanWithDistinct(plan: LogicalPlan): Boolean = {
    plan match {
      case PhysicalOperation(projectList, filters, child) if (child ne plan) =>
        isSupportedPhysicalOperation(plan, projectList, filters, child)
      case _: TiDBRelation => true
      case _ => false
    }
  }

  private def isSupportedAggregate(aggExpr: AggregateExpression): Boolean = {
    aggExpr.aggregateFunction match {
      case Average(_) | Sum(_) | Count(_) | Min(_) | Max(_) =>
        !aggExpr.isDistinct &&
          aggExpr.aggregateFunction
            .children
            .find(expr => !isSupportedBasicExpression(expr))
            .isEmpty
      case _ => false
    }
  }

  private def isSupportedBasicExpression(expr: Expression) = {
    expr match {
      case BasicExpression(_) => true
      case _ => false
    }
  }

  private def isSupportedProjection(expr: Expression): Boolean = {
    expr.find(child => !isSupportedBasicExpression(child)).isEmpty
  }

  private def isSupportedFilter(expr: Expression): Boolean = {
    isSupportedBasicExpression(expr)
  }

  // 1. if contains UDF / functions that cannot be folded
  private def isSupportedGroupingExpr(expr: Expression): Boolean = {
    isSupportedBasicExpression(expr)
  }

  class SelectBuilder {
    def toProtoByteString() = ByteString.EMPTY
  }

  def coprocessorReqToBytes(plan: LogicalPlan, builder: SelectBuilder = new SelectBuilder()): SelectBuilder = {
    plan match {
      case PhysicalAggregation(
      groupingExpressions, aggregateExpressions, _, child) =>
        // TODO: fill builder with value
        coprocessorReqToBytes(child, builder)

      case PhysicalOperation(projectList, filters, child) if (child ne plan) =>
        // TODO: fill builder with value
        coprocessorReqToBytes(child, builder)

      case logical.Limit(IntegerLiteral(_), logical.Sort(_, true, child)) =>
        // TODO: fill builder with value
        coprocessorReqToBytes(child, builder)

      case logical.Limit(IntegerLiteral(_),
      logical.Project(_, logical.Sort(_, true, child))) =>
        // TODO: fill builder with value
        coprocessorReqToBytes(child, builder)

      case logical.Limit(IntegerLiteral(_), child) =>
        // TODO: fill builder with value
        coprocessorReqToBytes(child, builder)

        // End of recursive traversal
      case LogicalRelation(_: CatalystSource, _, _) => builder
    }
  }

}
