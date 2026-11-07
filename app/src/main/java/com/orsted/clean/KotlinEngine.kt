package com.orsted.clean

import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.PieceType
import com.github.bhlangonijr.chesslib.Side
import com.github.bhlangonijr.chesslib.Square
import com.github.bhlangonijr.chesslib.move.Move
import kotlin.math.max
import kotlin.math.min

object KotlinEngine {
    
    fun getBestMove(fen: String): String {
        val board = Board()
        try {
            board.loadFromFen(fen)
        } catch (e: Exception) {
            return ""
        }

        val legalMoves = board.legalMoves()
        if (legalMoves.isEmpty()) return ""

        var bestMove = legalMoves[0]
        var bestScore = if (board.sideToMove == Side.WHITE) -99999 else 99999

        for (move in legalMoves) {
            board.doMove(move)
            // Depth 2 search is fast enough for mobile
            val score = minimax(board, 2, -99999, 99999, board.sideToMove == Side.WHITE)
            board.undoMove()

            if (board.sideToMove == Side.WHITE) {
                if (score > bestScore) { bestScore = score; bestMove = move }
            } else {
                if (score < bestScore) { bestScore = score; bestMove = move }
            }
        }

        return bestMove.toString() // Returns e2e4 format
    }

    private fun minimax(board: Board, depth: Int, alpha: Int, beta: Int, isMaximizing: Boolean): Int {
        if (depth == 0 || board.isMated || board.isDraw) return evaluateBoard(board)
        
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
        var score = 0
        // Loop through all 64 squares using the library's Square enum
        for (square in Square.values()) {
            val piece = board.getPiece(square)
            if (piece != Piece.NONE) {
                val valScore = when (piece.pieceType) {
                    PieceType.PAWN -> 100
                    PieceType.KNIGHT -> 320
                    PieceType.BISHOP -> 330
                    PieceType.ROOK -> 500
                    PieceType.QUEEN -> 900
                    PieceType.KING -> 20000
                    else -> 0
                }
                if (piece.pieceSide == Side.WHITE) score += valScore else score -= valScore
            }
        }
        return score
    }
}
