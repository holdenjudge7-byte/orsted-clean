package com.orsted.clean

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object KotlinEngine {
    private val pieceValues = intArrayOf(100, 320, 330, 500, 900, 20000) // P, N, B, R, Q, K

    fun getBestMove(fen: String): String {
        val parts = fen.split(" ")
        val turn = if (parts[1] == "w") 1 else -1
        val board = parseFen(parts[0])
        val bestMove = findBestMove(board, turn, 3) // Depth 3 (adjustable)
        return bestMove ?: ""
    }

    private fun findBestMove(board: Array<CharArray>, turn: Int, depth: Int): String? {
        var bestScore = if (turn == 1) -999999 else 999999
        var bestMove: String? = null

        // Simple pseudo-legal move generation for demonstration
        for (r in 0..7) {
            for (c in 0..7) {
                val piece = board[r][c]
                if (isWhite(piece) && turn == 1 || isBlack(piece) && turn == -1) {
                    val moves = generateMoves(board, r, c)
                    for (move in moves) {
                        val captured = board[move[0]][move[1]]
                        board[move[0]][move[1]] = piece
                        board[r][c] = ' '
                        val score = minimax(board, depth - 1, -999999, 999999, -turn)
                        board[r][c] = piece
                        board[move[0]][move[1]] = captured

                        if (turn == 1 && score > bestScore) {
                            bestScore = score
                            bestMove = squareName(r, c) + squareName(move[0], move[1])
                        } else if (turn == -1 && score < bestScore) {
                            bestScore = score
                            bestMove = squareName(r, c) + squareName(move[0], move[1])
                        }
                    }
                }
            }
        }
        return bestMove
    }

    private fun minimax(board: Array<CharArray>, depth: Int, alpha: Int, beta: Int, turn: Int): Int {
        if (depth == 0) return evaluate(board)
        var a = alpha
        var b = beta
        var bestScore = if (turn == 1) -999999 else 999999

        for (r in 0..7) {
            for (c in 0..7) {
                val piece = board[r][c]
                if (isWhite(piece) && turn == 1 || isBlack(piece) && turn == -1) {
                    val moves = generateMoves(board, r, c)
                    for (move in moves) {
                        val captured = board[move[0]][move[1]]
                        board[move[0]][move[1]] = piece
                        board[r][c] = ' '
                        val score = minimax(board, depth - 1, a, b, -turn)
                        board[r][c] = piece
                        board[move[0]][move[1]] = captured

                        if (turn == 1) {
                            bestScore = max(bestScore, score)
                            a = max(a, bestScore)
                        } else {
                            bestScore = min(bestScore, score)
                            b = min(b, bestScore)
                        }
                        if (b <= a) break
                    }
                }
            }
        }
        return bestScore
    }

    private fun evaluate(board: Array<CharArray>): Int {
        var score = 0
        for (r in 0..7) {
            for (c in 0..7) {
                val p = board[r][c]
                when (p) {
                    'P' -> score += 100 + (7 - r)
                    'p' -> score -= 100 + r
                    'N' -> score += 320
                    'n' -> score -= 320
                    'B' -> score += 330
                    'b' -> score -= 330
                    'R' -> score += 500
                    'r' -> score -= 500
                    'Q' -> score += 900
                    'q' -> score -= 900
                }
            }
        }
        return score
    }

    private fun generateMoves(board: Array<CharArray>, r: Int, c: Int): List<IntArray> {
        val moves = mutableListOf<IntArray>()
        val p = board[r][c].lowercaseChar()
        val isWhiteTurn = isWhite(board[r][c])

        when (p) {
            'p' -> {
                val dir = if (isWhiteTurn) -1 else 1
                if (isValid(r + dir, c) && board[r + dir][c] == ' ') moves.add(intArrayOf(r + dir, c))
                // Add captures here for full implementation
            }
            'n' -> {
                val dr = intArrayOf(-2, -2, -1, -1, 1, 1, 2, 2)
                val dc = intArrayOf(-1, 1, -2, 2, -2, 2, -1, 1)
                for (i in 0..7) if (isValid(r + dr[i], c + dc[i])) moves.add(intArrayOf(r + dr[i], c + dc[i]))
            }
            'r', 'b', 'q' -> {
                // Sliding pieces simplified for brevity (assumes empty squares)
                val dirs = listOf(intArrayOf(1,0), intArrayOf(-1,0), intArrayOf(0,1), intArrayOf(0,-1), intArrayOf(1,1), intArrayOf(-1,-1), intArrayOf(1,-1), intArrayOf(-1,1))
                for (d in dirs) {
                    var nr = r + d[0]; var nc = c + d[1]
                    while (isValid(nr, nc)) { moves.add(intArrayOf(nr, nc)); nr += d[0]; nc += d[1] }
                }
            }
            'k' -> {
                for (dr in -1..1) for (dc in -1..1) if ((dr != 0 || dc != 0) && isValid(r + dr, c + dc)) moves.add(intArrayOf(r + dr, c + dc))
            }
        }
        return moves.filter { isValid(it[0], it[1]) }
    }

    private fun isWhite(p: Char) = p.isUpperCase()
    private fun isBlack(p: Char) = p.isLowerCase() && p != ' '
    private fun isValid(r: Int, c: Int) = r in 0..7 && c in 0..7
    private fun squareName(r: Int, c: Int) = "${('a' + c)}${(8 - r)}"

    private fun parseFen(fen: String): Array<CharArray> {
        val board = Array(8) { CharArray(8) { ' ' } }
        var r = 0; var c = 0
        for (char in fen) {
            if (char == '/') { r++; c = 0 }
            else if (char.isDigit()) { c += char.toString().toInt() }
            else { if (r < 8 && c < 8) board[r][c] = char; c++ }
        }
        return board
    }
}
