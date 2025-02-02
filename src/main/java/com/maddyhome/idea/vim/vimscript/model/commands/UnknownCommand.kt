/*
 * IdeaVim - Vim emulator for IDEs based on the IntelliJ platform
 * Copyright (C) 2003-2021 The IdeaVim authors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.maddyhome.idea.vim.vimscript.model.commands

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.maddyhome.idea.vim.VimPlugin
import com.maddyhome.idea.vim.common.GoalCommand
import com.maddyhome.idea.vim.ex.ExException
import com.maddyhome.idea.vim.ex.InvalidCommandException
import com.maddyhome.idea.vim.ex.ranges.Ranges
import com.maddyhome.idea.vim.helper.MessageHelper
import com.maddyhome.idea.vim.helper.Msg
import com.maddyhome.idea.vim.vimscript.model.ExecutionResult
import com.maddyhome.idea.vim.vimscript.model.commands.UnknownCommand.Constants.MAX_RECURSION
import com.maddyhome.idea.vim.vimscript.parser.VimscriptParser

/**
 * any command with no parser rule. we assume that it is an alias
 */
data class UnknownCommand(val ranges: Ranges, val name: String, val argument: String) :
  Command.SingleExecution(ranges, argument) {
  override val argFlags = flags(RangeFlag.RANGE_OPTIONAL, ArgumentFlag.ARGUMENT_OPTIONAL, Access.SELF_SYNCHRONIZED)

  private object Constants {
    const val MAX_RECURSION = 100
  }

  override fun processCommand(editor: Editor, context: DataContext): ExecutionResult {
    return processPossiblyAliasCommand("$name $argument", editor, context, MAX_RECURSION)
  }

  private fun processPossiblyAliasCommand(name: String, editor: Editor, context: DataContext, aliasCountdown: Int): ExecutionResult {
    if (VimPlugin.getCommand().isAlias(name)) {
      if (aliasCountdown > 0) {
        val commandAlias = VimPlugin.getCommand().getAliasCommand(name, 1)
        when (commandAlias) {
          is GoalCommand.Ex -> {
            if (commandAlias.command.isEmpty()) {
              val message = MessageHelper.message(Msg.NOT_EX_CMD, name)
              throw InvalidCommandException(message, null)
            }
            val parsedCommand = VimscriptParser.parseCommand(commandAlias.command) ?: throw ExException("E492: Not an editor command: ${commandAlias.command}")
            return if (parsedCommand is UnknownCommand) {
              processPossiblyAliasCommand(commandAlias.command, editor, context, aliasCountdown - 1)
            } else {
              parsedCommand.parent = this.parent
              parsedCommand.execute(editor, context)
              ExecutionResult.Success
            }
          }
          is GoalCommand.Call -> {
            commandAlias.handler.execute(editor, context)
            return ExecutionResult.Success
          }
        }
      } else {
        VimPlugin.showMessage(MessageHelper.message("recursion.detected.maximum.alias.depth.reached"))
        VimPlugin.indicateError()
        return ExecutionResult.Error
      }
    } else {
      throw ExException("E492: Not an editor command: $name")
    }
  }
}
