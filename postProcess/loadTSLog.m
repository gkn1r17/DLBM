%Loads CSV diversity/richness file: called by convert files

function [diversity,limits] = loadTSLog(dirname, fname, numBoxes)

    limits = nan(1,numBoxes);
    locals = [];
    globals = [];

    for n = 0:6
        filename = dirname + "/" + fname + "_N" + n + ".csv";
        
        if isfile(filename)
            tsLogData = readtable(dirname + "/" + fname + "_N" + n + ".csv", 'NumHeaderLines', 0);
            localIdx = find(tsLogData.Var1 == "day");
            globalIdx = find(tsLogData.Var1 == "global");

            tsLogData = readmatrix(dirname + "/" + fname + "_N" + n + ".csv", 'NumHeaderLines', 0);
            
            nodeIDs = tsLogData(1,4:end);

            nodeIDs = nodeIDs(~isnan(nodeIDs));
            lim = tsLogData(2,4:end);
            lim = lim(~isnan(lim));

            limits(1, nodeIDs + 1) = lim;

            
            tsDays = tsLogData(localIdx,2);
            
            if(isempty(locals))
                locals = nan(numel(tsDays) ,numBoxes);
            end

            locals(: , nodeIDs + 1) = tsLogData(localIdx,7:end);
                
            if(~isempty(globalIdx))
                globals = tsLogData(globalIdx,2);
            end
        end
        
    end

    diversity = struct();
    diversity.globals = globals;
    diversity.locals = locals;
    diversity.days = tsDays;

end
