package com.orsted.clean

import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.move.Move
import kotlin.random.Random
import kotlin.math.max
import kotlin.math.min

object KotlinEngine {
    
    // Porting the v1.0.16 Blunder Math
    fun getBestMove(fen: String, targetElo: Int): String {
        val board = Board()
        try {
            board.loadFromFen(fen)
        } catch (e: Exception) {
            return ""
        }

        val legalMoves = board.legalMoves()
        if (legalMoves.isEmpty()) return ""

        // Evaluate all legal moves to find the best one
        var bestMove = legalMoves[0]
        var bestScore = if (board.sideToMove == com.github.bhlangonijr.chesslib.Side.WHITE) -99999 else 99999

        val evaluatedMoves = mutableListOf<Pair<Move, Int>>()

        for (move in legalMoves) {
            board.doMove(move)
            // Depth 2 for speed on Oppo A5s
            val score = minimax(board, 2, -99999, 99999, board.sideToMove == com.github.bhlangonijr.chesslib.Side.WHITE)
            board.undoMove()
            
            evaluatedMoves.add(Pair(move, score))

            if (board.sideToMove == com.github.bhlangonijr.chesslib.Side.WHITE) {
                if (score > bestScore) { bestScore = score; bestMove = move }
            } else {
                if (score < bestScore) { bestScore = score; bestMove = move }
            }
        }

        // Apply v1.0.16 Blunder Math
        if (targetElo < 2800 && evaluatedMoves.size > 1) {
            evaluatedMoves.sortByDescending { it.second } // Sort best to worst for White
            val sortedForTurn = if (board.sideToMove == com.github.bhlangonijr.chesslib.Side.WHITE) evaluatedMoves else evaluatedMoves.reversed()
            
            val eloFactor = (2800 - targetElo) / 2000.0
            val missProb = eloFactor * 0.35
            val maxDrop = (eloFactor * 3.0 * 100).toInt() // Convert pawns to engine score

            if (Random.nextDouble() < missProb) {
                val bestEval = sortedForTurn[0].second
                // Find moves that aren't terrible, but aren't the best
                val candidates = sortedForTurn.drop(1).filter { 
                    val drop = kotlin.math.abs(bestEval - it.second)
                    drop > 0 && drop <= maxDrop 
                }
                
                if (candidates.isNotEmpty()) {
                    // Pick a random blunder
                    val pickIdx = (Math.pow(Random.nextDouble(), 1.5) * candidates.size).toInt().coerceIn(0, candidates.size - 1)
                    bestMove = candidates[pickIdx].first
                }
            }
        }

        return bestMove.toString() // Returns e2e4 format
    }

    private fun minimax(board: Board, depth: Int, alpha: Int, beta: Int, isMaximizing: Boolean): Int {
        if (depth == 0 || board.isMated || board.isDraw) {
            return evaluateBoard(board)
        }
        var a = alpha
        var b = beta
        val moves = board.legalMoves()

        if (isMaximizing) {
            var maxEval = -99999
            for (move in moves) {
                board.doMove(move)
                val eval = minimax(board, depth - 1, a, b, false)
                board.undoMove()
                maxEval = max(maxEval, eval)
                a = max(a, eval)
                if (b <= a) break
            }
            return maxEval
        } else {
            var minEval = 99999
            for (move in moves) {
                board.doMove(move)
                val eval = minimax(board, depth - 1, a, b, true)
                board.undoMove()
                minEval = min(minEval, eval)
                b = min(b, eval)
                if (b <= a) break
            }
            return minEval
        }
    }

    private fun evaluateBoard(board: Board): Int {
        // Simple piece evaluation for speed
        var score = 0
        val pieces = board.boardArray
        for (i in pieces.indices) {
            val piece = pieces[i]
            if (piece != null) {
                val valScore = when (piece.pieceType) {
                    com.github.bhlangonijr.chesslib.PieceType.PAWN -> 100
                    com.github.bhlangonijr.chesslib.PieceType.KNIGHT -> 320
                    com.github.bhlangonijr.chesslib.PieceType.BISHOP -> 330
                    com.github.bhlangonijr.chesslib.PieceType.ROOK -> 500
                    com.github.bhlangonijr.chesslib.PieceType.QUEEN -> 900
                    com.github.bhlangonijr.chesslib.PieceType.KING -> 20000
                    else -> 0
                }
                if (piece.pieceSide == com.github.bhlangonijr.chesslib.Side.WHITE) score += valScore
                else score -= valScore
            }
        }
        return score
    }
}
