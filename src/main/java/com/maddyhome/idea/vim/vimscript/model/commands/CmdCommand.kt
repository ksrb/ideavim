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
import com.intellij.openapi.util.NlsSafe
import com.maddyhome.idea.vim.VimPlugin
import com.maddyhome.idea.vim.common.CommandAlias
import com.maddyhome.idea.vim.ex.ExOutputModel
import com.maddyhome.idea.vim.ex.ranges.Ranges
import com.maddyhome.idea.vim.group.CommandGroup.Companion.BLACKLISTED_ALIASES
import com.maddyhome.idea.vim.helper.MessageHelper
import com.maddyhome.idea.vim.vimscript.model.ExecutionResult

/**
 * @author Elliot Courant
 * see "h :command"
 */
data class CmdCommand(val ranges: Ranges, val argument: String) : Command.SingleExecution(ranges) {
  override val argFlags = flags(RangeFlag.RANGE_FORBIDDEN, ArgumentFlag.ARGUMENT_OPTIONAL, Access.READ_ONLY)

  // Static definitions needed for aliases.
  private companion object {
    const val overridePrefix = "!"

    @NlsSafe
    const val argsPrefix = "-nargs"

    const val anyNumberOfArguments = "*"
    const val zeroOrOneArguments = "?"
    const val moreThanZeroArguments = "+"
  }
  override fun processCommand(editor: Editor, context: DataContext): ExecutionResult {
    val result: Boolean = if (argument.trim().isEmpty()) {
      this.listAlias(editor, "")
    } else {
      this.addAlias(editor)
    }
    return if (result) ExecutionResult.Success else ExecutionResult.Error
  }

  private fun listAlias(editor: Editor, filter: String): Boolean {
    val lineSeparator = "\n"
    val allAliases = VimPlugin.getCommand().listAliases()
    val aliases = allAliases.filter {
      (filter.isEmpty() || it.key.startsWith(filter))
    }.map {
      "${it.key.padEnd(12)}${it.value.numberOfArguments.padEnd(11)}${it.value.printValue()}"
    }.sortedWith(String.CASE_INSENSITIVE_ORDER).joinToString(lineSeparator)
    ExOutputModel.getInstance(editor).output("Name        Args       Definition$lineSeparator$aliases")
    return true
  }

  private fun addAlias(editor: Editor?): Boolean {
    var argument = argument.trim()

    // Handle overwriting of aliases
    val overrideAlias = argument.startsWith(overridePrefix)
    if (overrideAlias) {
      argument = argument.removePrefix(overridePrefix).trim()
    }

    // Handle alias arguments
    val hasArguments = argument.startsWith(argsPrefix)
    var minNumberOfArgs = 0
    var maxNumberOfArgs = 0
    if (hasArguments) {
      // Extract the -nargs that's part of this execution, it's possible that -nargs is
      // in the actual alias being created, and we don't want to parse that one.
      val trimmedInput = argument.takeWhile { it != ' ' }
      val pattern = Regex("(?>-nargs=((|[-])\\d+|[?]|[+]|[*]))").find(trimmedInput) ?: run {
        VimPlugin.showMessage(MessageHelper.message("e176.invalid.number.of.arguments"))
        return false
      }
      val nargForTrim = pattern.groupValues[0]
      val argumentValue = pattern.groups[1]!!.value
      val argNum = argumentValue.toIntOrNull()
      if (argNum == null) { // If the argument number is null then it is not a number.
        // Make sure the argument value is a valid symbol that we can handle.
        when (argumentValue) {
          anyNumberOfArguments -> {
            minNumberOfArgs = 0
            maxNumberOfArgs = -1
          }
          zeroOrOneArguments -> maxNumberOfArgs = 1
          moreThanZeroArguments -> {
            minNumberOfArgs = 1
            maxNumberOfArgs = -1
          }
          else -> {
            // Technically this should never be reached, but is here just in case
            // I missed something, since the regex limits the value to be ? + * or
            // a valid number, its not possible (as far as I know) to have another value
            // that regex would accept that is not valid.
            VimPlugin.showMessage(MessageHelper.message("e176.invalid.number.of.arguments"))
            return false
          }
        }
      } else {
        // Not sure why this isn't documented, but if you try to create a command in vim
        // with an explicit number of arguments greater than 1 it returns this error.
        if (argNum > 1 || argNum < 0) {
          VimPlugin.showMessage(MessageHelper.message("e176.invalid.number.of.arguments"))
          return false
        }
        minNumberOfArgs = argNum
        maxNumberOfArgs = argNum
      }
      argument = argument.removePrefix(nargForTrim).trim()
    }

    // We want to trim off any "!" at the beginning of the arguments.
    // This will also remove any extra spaces.
    argument = argument.trim()

    // We want to get the first character sequence in the arguments.
    // eg. command! Wq wq
    // We want to extract the Wq only, and then just use the rest of
    // the argument as the alias result.
    val alias = argument.split(" ")[0]
    argument = argument.removePrefix(alias).trim()

    // User-aliases need to begin with an uppercase character.
    if (!alias[0].isUpperCase()) {
      VimPlugin.showMessage(MessageHelper.message("e183.user.defined.commands.must.start.with.an.uppercase.letter"))
      return false
    }

    if (alias in BLACKLISTED_ALIASES) {
      VimPlugin.showMessage(MessageHelper.message("e841.reserved.name.cannot.be.used.for.user.defined.command"))
      return false
    }

    if (argument.isEmpty()) {
      if (editor == null) {
        // If there is no editor then we can't list aliases, just return false.
        // No message should be shown either, since there is no editor.
        return false
      }
      return this.listAlias(editor, alias)
    }

    // If we are not over-writing existing aliases, and an alias with the same command
    // already exists then we want to do nothing.
    if (!overrideAlias && VimPlugin.getCommand().hasAlias(alias)) {
      VimPlugin.showMessage(MessageHelper.message("e174.command.already.exists.add.to.replace.it"))
      return false
    }

    // Store the alias and the command. We don't need to parse the argument
    // at this time, if the syntax is wrong an error will be returned when
    // the alias is executed.
    VimPlugin.getCommand().setAlias(alias, CommandAlias.Ex(minNumberOfArgs, maxNumberOfArgs, alias, argument))

    return true
  }
}
